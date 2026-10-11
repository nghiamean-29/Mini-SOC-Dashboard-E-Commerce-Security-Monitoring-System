# ENVIRONMENT SETUP — Chuẩn bị môi trường chạy dự án

> Tài liệu này là **bước chuẩn bị của Phase 1** (không phải code dự án).
> Bạn cần hoàn thành trước khi chạy `mvn spring-boot:run` lần đầu.
>
> **Cấu hình đã chốt (D-3, D-8):** JDK 21 + Maven, **không Docker** → dùng **PostgreSQL cài trực tiếp**.
> `docker-compose.yml` vẫn sẽ được tạo ở Phase 13 theo yêu cầu SDD §13 (đường dự phòng / để đưa vào báo cáo).

---

## 0. Checklist tổng quan

| # | Thành phần | Version chốt | Bắt buộc? | Kiểm tra bằng |
|---|---|---|---|---|
| 1 | JDK | **21 LTS** (Temurin) | ✅ Bắt buộc | `java -version` |
| 2 | Maven | **3.9.16** | ✅ Bắt buộc | `mvn -v` |
| 3 | PostgreSQL | **18.x** (tối thiểu 14) | ✅ Bắt buộc | `psql --version` |
| 4 | Database + role `minisoc` | — | ✅ Bắt buộc | `psql -h localhost -U minisoc -d minisoc_db -c "select version()"` |
| 5 | Công cụ xem DB (pgAdmin/DBeaver) | — | ⬜ Tuỳ chọn | mở được bảng `security_events` |
| 6 | Git | bất kỳ | ✅ Bắt buộc | `git --version` |
| 7 | Docker Desktop | — | ⬜ **Không cần** | chỉ dùng ở Phase 13 nếu muốn |

**Vì sao chọn Maven 3.9.16 mà không phải Maven 4?** Maven 4.0 tại thời điểm 2026-09 vẫn ở bản **RC** (chưa GA). Dùng RC nghĩa là có thể gặp lỗi plugin mà tài liệu chưa cập nhật — rủi ro không cần thiết cho đồ án. Maven 3.9.16 là bản GA ổn định và Spring Boot 4.1 hỗ trợ đầy đủ.

**Vì sao PostgreSQL tối thiểu 14?** PostgreSQL 14 sẽ hết hỗ trợ (EOL) tháng 11/2026 — nếu máy bạn chỉ có 14 thì dự án vẫn chạy, nhưng nên nâng lên 16/17/18. Dự án dùng `JSONB`, `ON CONFLICT`, `TIMESTAMPTZ` — đều có từ PostgreSQL 9.x nên không bị ràng buộc nhiều.

---

## 1. Cài JDK 21

### 1.1. Windows

**Cách 1 — winget (khuyến nghị, có sẵn trên Windows 10/11):**

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

**Cách 2 — tải trực tiếp:** vào <https://adoptium.net/temurin/releases/?version=21>, chọn **Operating System: Windows**, **Architecture: x64**, **Package Type: JDK**, tải file `.msi` và cài (nhớ tick **"Set JAVA_HOME variable"** và **"Add to PATH"** trong bước tuỳ chọn của installer).

### 1.2. macOS

```bash
# Homebrew
brew install --cask temurin

# hoặc SDKMAN (quản lý nhiều version Java)
curl -s "https://get.sdkman.io" | bash
sdk install java 21.0.9-tem   # nếu version này không còn, chạy `sdk list java` rồi chọn bản 21.x mới nhất
```

### 1.3. Linux (Ubuntu/Debian)

```bash
# Ubuntu 24.04+ có sẵn trong repo
sudo apt update && sudo apt install -y openjdk-21-jdk

# Debian 12 hoặc bản Ubuntu cũ: dùng SDKMAN (an toàn, không đụng hệ thống)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.9-tem
```

### 1.4. Xác nhận

```bash
java -version
# Kỳ vọng: openjdk version "21.x.x" ... (KHÔNG được là 17 hoặc 24)
javac -version
```

> ⚠️ Nếu `java -version` ra 17 hoặc bản khác: máy bạn đang có nhiều JDK. Cách xử lý: đặt `JAVA_HOME` trỏ tới JDK 21 (xem §5) và đảm bảo `%JAVA_HOME%\bin` (Windows) hoặc `$JAVA_HOME/bin` (Linux/macOS) đứng **trước** trong `PATH`.

---

## 2. Cài Maven 3.9.16

### 2.1. Windows

```powershell
winget install Apache.Maven
# hoặc nếu dùng Chocolatey: choco install maven
```

Nếu cài thủ công: tải `apache-maven-3.9.16-bin.zip` từ <https://maven.apache.org/download.cgi>, giải nén vào `C:\Tools\apache-maven-3.9.16`, rồi thêm `C:\Tools\apache-maven-3.9.16\bin` vào biến `Path`.

### 2.2. macOS / Linux

```bash
# macOS
brew install maven

# SDKMAN (khuyến nghị trên Linux — luôn được bản mới nhất)
sdk install maven 3.9.16
```

> Đừng dùng `sudo apt install maven` trên Ubuntu cũ: bản trong repo có thể là 3.6.x (quá cũ, plugin Spring Boot có thể cảnh báo). Nếu lỡ cài rồi thì vẫn dùng được, nhưng nên ưu tiên SDKMAN.

### 2.3. Xác nhận — **quan trọng, kiểm tra cả dòng Java**

```bash
mvn -v
```
Kỳ vọng (2 dòng đầu là quan trọng nhất):
```
Apache Maven 3.9.16 (...)
Maven home: ...
Java version: 21.x.x, vendor: Eclipse Adoptium      <-- phải là 21
Default locale: ..., platform encoding: UTF-8       <-- phải là UTF-8
```

⚠️ Nếu `Java version` ở đây khác 21 → Maven đang dùng JDK khác. Sửa `JAVA_HOME` (§5) rồi chạy lại `mvn -v`.

⚠️ Nếu `platform encoding` không phải UTC-8/UTF-8: với `MAVEN_OPTS` hoặc `JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8`. Dự án có tiếng Việt trong template nên encoding sai sẽ gây lỗi hiển thị.

---

## 3. Cài PostgreSQL 18

Bạn không dùng Docker, nên cài trực tiếp. Chọn **một** trong các cách dưới.

### 3.1. Windows

1. Tải installer EDB từ <https://www.postgresql.org/download/windows/> (hoặc `winget search postgresql` để tìm id chính xác của bản 18 rồi `winget install <id>`).
2. Chạy installer:
   * **Port:** giữ `5432`.
   * **Superuser password:** đặt mật khẩu mạnh, **ghi nhớ** (đây là mật khẩu của user `postgres`).
   * **Locale:** để mặc định (hoặc `English, United States`, encoding `UTF8`).
3. Cài xong, PostgreSQL chạy như một **Windows Service** (tên `postgresql-x64-18`). Kiểm tra trong `services.msc` hoặc:
   ```powershell
   Get-Service postgresql*
   ```
4. Thêm `C:\Program Files\PostgreSQL\18\bin` vào `Path` để dùng được `psql`.

### 3.2. macOS

```bash
brew install postgresql@18
brew services start postgresql@18          # chạy nền và tự khởi động lại cùng máy
echo 'export PATH="/opt/homebrew/opt/postgresql@18/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```
> Trên macOS, user mặc định của PostgreSQL là **tên user macOS của bạn** (không phải `postgres`).

### 3.3. Linux (Ubuntu/Debian) — dùng repo chính thức PGDG

```bash
# 1) Thêm kho PGDG (repo chính thức của PostgreSQL)
sudo install -d /usr/share/postgresql-common/pgdg
sudo curl -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc --fail \
  https://www.postgresql.org/media/keys/ACCC4CF8.asc
sudo sh -c 'echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] \
  https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" \
  > /etc/apt/sources.list.d/pgdg.list'

# 2) Cài
sudo apt update
sudo apt install -y postgresql-18 postgresql-client-18

# 3) Khởi động
sudo systemctl enable --now postgresql
```

### 3.4. Xác nhận PostgreSQL đang chạy

```bash
psql --version
# Kỳ vọng: psql (PostgreSQL) 18.x
```

| Hệ điều hành | Lệnh kiểm tra service |
|---|---|
| Windows | `Get-Service postgresql*` (Status phải là `Running`) |
| macOS | `brew services list` |
| Linux | `systemctl status postgresql` |

Nếu `psql: command not found`:
* Windows: thêm `C:\Program Files\PostgreSQL\18\bin` vào `Path`, mở lại terminal.
* Linux: dùng đường dẫn đầy đủ `/usr/lib/postgresql/18/bin/psql` hoặc cài `postgresql-client-18`.

---

## 4. Tạo database và role cho dự án

Dự án cần: **database** `minisoc_db` + **role** `minisoc` (owner). Flyway sẽ tự tạo bảng bên trong database đó — **bạn không cần tạo bảng bằng tay**.

### 4.1. Mở psql bằng superuser

| Hệ điều hành | Lệnh |
|---|---|
| Windows | `psql -U postgres -h localhost` (nhập mật khẩu đã đặt khi cài) |
| macOS | `psql -d postgres` (user = tên bạn) |
| Linux | `sudo -u postgres psql` |

### 4.2. Chạy SQL sau (đổi mật khẩu thành mật khẩu của bạn)

```sql
-- 1) Tạo role đăng nhập cho ứng dụng (KHÔNG dùng superuser cho app!)
CREATE ROLE minisoc LOGIN PASSWORD 'DoiMatKhauNay_2026';

-- 2) Tạo database thuộc sở hữu của role đó
CREATE DATABASE minisoc_db OWNER minisoc ENCODING 'UTF8';

-- 3) Kiểm tra
\l minisoc_db
\du minisoc
```

> **Vì sao không dùng user `postgres` cho ứng dụng?** Nguyên tắc **least privilege**: ứng dụng chỉ cần quyền trên một database, không cần quyền superuser. Nếu ứng dụng bị khai thác, thiệt hại bị giới hạn. Đây cũng là một điểm bạn nên nói khi bảo vệ đồ án (mục Security).

### 4.3. Xác nhận kết nối bằng chính role của ứng dụng

```bash
psql -h localhost -p 5432 -U minisoc -d minisoc_db -c "SELECT version();"
```
Kỳ vọng: in ra `PostgreSQL 18.x ...`. Nếu bị hỏi mật khẩu → nhập mật khẩu vừa tạo (đúng như mong đợi).

⚠️ Nếu báo `FATAL: password authentication failed for user "minisoc"`:
* Kiểm tra bạn đã `CREATE ROLE ... LOGIN PASSWORD ...` chưa (`\du` trong psql superuser).
* Trên Linux, file `pg_hba.conf` cần dòng `host all all 127.0.0.1/32 scram-sha-256` (đây là mặc định của bản cài mới). Nếu bị sửa thành `peer`/`trust` thì đó là nguyên nhân. Đường dẫn: `/etc/postgresql/18/main/pg_hba.conf`. Sau khi sửa: `sudo systemctl reload postgresql`.

---

## 5. Biến môi trường (đây là cách dự án tránh hard-code secret — NFR-SEC-02)

Ứng dụng sẽ đọc cấu hình từ ENV với tên **cố định** sau:

| Biến | Ví dụ giá trị | Ý nghĩa |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/minisoc_db` | Chuỗi kết nối JDBC |
| `DB_USERNAME` | `minisoc` | Role của ứng dụng |
| `DB_PASSWORD` | `DoiMatKhauNay_2026` | Mật khẩu (⚠️ **không** commit vào git) |
| `APP_SIMULATOR_ENABLED` | `true` (dev) / `false` (prod) | Bật/tắt Attack Simulator |
| `SPRING_PROFILES_ACTIVE` | `dev` | Chọn profile cấu hình |

### 5.1. Windows PowerShell (chỉ có tác dụng trong cửa sổ hiện tại)

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/minisoc_db"
$env:DB_USERNAME="minisoc"
$env:DB_PASSWORD="DoiMatKhauNay_2026"
$env:SPRING_PROFILES_ACTIVE="dev"
```

Đặt vĩnh viễn (áp dụng cho các lần mở terminal sau — cần mở lại terminal):
```powershell
setx DB_USERNAME "minisoc"
setx DB_URL "jdbc:postgresql://localhost:5432/minisoc_db"
# Lưu ý: setx DB_PASSWORD sẽ ghi mật khẩu vào registry của user — chấp nhận được cho máy cá nhân,
# nhưng AN TOÀN HƠN là dùng file .env (xem 5.3) hoặc chỉ set trong phiên làm việc.
```

### 5.2. macOS / Linux

```bash
export DB_URL="jdbc:postgresql://localhost:5432/minisoc_db"
export DB_USERNAME="minisoc"
export DB_PASSWORD="DoiMatKhauNay_2026"
export SPRING_PROFILES_ACTIVE="dev"
```
Thêm vào `~/.bashrc` / `~/.zshrc` nếu muốn giữ vĩnh viễn (⚠️ nhớ rằng file này có mật khẩu ⇒ **không** đưa vào git, và đừng làm vậy trên máy dùng chung).

### 5.3. Cách gọn hơn: file `.env` không được commit

Ở Phase 1 tôi sẽ tạo:
* `config/env/dev.env.example` — file mẫu, **commit** được, chỉ có giá trị giả.
* `config/env/dev.env` — file thật của bạn, **nằm trong `.gitignore`**.

Trên Linux/macOS bạn nạp bằng:
```bash
set -a; source config/env/dev.env; set +a
```

> **Kiểm tra nhanh xem bạn có vô tình commit mật khẩu không (chạy trước mỗi lần push):**
> ```bash
> git grep -nEi "password|secret|token" -- '*.yml' '*.yaml' '*.properties' '*.java' | grep -v "DB_PASSWORD\|\${" 
> ```
> Nếu dòng nào lộ giá trị thật → sửa trước khi commit. Ở Phase 12 tôi sẽ có checklist đầy đủ cho việc này.

---

## 6. Xem dữ liệu trong database (tuỳ chọn nhưng rất nên có)

Khi học JPA, việc **nhìn thấy bảng thật** giúp hiểu ngay mapping có đúng không.

| Công cụ | Ghi chú |
|---|---|
| **pgAdmin 4** | Cài kèm installer Windows; GUI đầy đủ, hơi nặng |
| **DBeaver Community** | Đa nền tảng (Win/mac/Linux), miễn phí, nhẹ hơn — tôi khuyên dùng bản này |
| **psql** | Dòng lệnh, luôn sẵn; dùng `\dt` để xem bảng, `\d security_events` để xem cấu trúc |

Ví dụ các câu lệnh sẽ dùng để kiểm chứng dự án:
```sql
\dt                                          -- liệt kê bảng do Flyway tạo
\d security_events                            -- xem cấu trúc + index
SELECT event_type, count(*) FROM security_events GROUP BY 1 ORDER BY 2 DESC;
SELECT alert_type, severity, status, count(*) FROM security_alerts GROUP BY 1,2,3;
SELECT * FROM flyway_schema_history ORDER BY installed_rank;   -- xem lịch sử migration
```

---

## 7. Kiểm tra tổng thể (chạy 5 lệnh này rồi dán kết quả cho tôi)

```bash
java -version            # (1) phải là 21.x
mvn -v                   # (2) Maven 3.9.16 + Java version: 21.x
psql --version           # (3) psql (PostgreSQL) 18.x
psql -h localhost -U minisoc -d minisoc_db -c "SELECT current_database(), current_user;"
                         # (4) minisoc_db | minisoc
git --version            # (5) bất kỳ bản nào
```

Nếu cả 5 lệnh đều đúng kỳ vọng → **môi trường đã sẵn sàng cho Phase 1**. Bạn gửi kết quả cho tôi, tôi sẽ đối chiếu trước khi bạn chạy `mvn clean compile`.

---

## 8. Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân thường gặp | Cách sửa |
|---|---|---|
| `java -version` ra 17/24 | Máy có nhiều JDK, `PATH`/`JAVA_HOME` trỏ sai | Đặt `JAVA_HOME` → JDK 21; trên Windows sửa trong "Edit environment variables for your account" |
| `mvn -v` báo Java 17 dù `java -version` là 21 | Maven dùng `JAVA_HOME`, không dùng `PATH` | Sửa `JAVA_HOME` (Windows: `setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-21.x.x-hotspot"`) |
| `mvn: command not found` | Chưa thêm `bin` của Maven vào `PATH` | Thêm và **mở lại terminal** |
| `psql: command not found` | Chưa thêm `bin` của PostgreSQL vào `PATH` | Xem §3.4 |
| `connection refused` khi kết nối DB | Service PostgreSQL chưa chạy | Windows: `Start-Service postgresql-x64-18`; macOS: `brew services start postgresql@18`; Linux: `sudo systemctl start postgresql` |
| `FATAL: password authentication failed` | Sai mật khẩu, hoặc `pg_hba.conf` để `peer`/`trust` | Xem §4.3 |
| `port 5432 already in use` | Có sẵn một PostgreSQL khác đang chạy | Đổi port trong `postgresql.conf` (ví dụ 5433) rồi đặt lại `DB_URL` |
| Ký tự tiếng Việt bị lỗi khi lưu vào DB | Encoding của DB hoặc của JVM không phải UTF-8 | Kiểm tra `ENCODING 'UTF8'` khi tạo DB; `JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8` |
| `UnsupportedClassVersionError` khi chạy | Biên dịch bằng JDK 21 nhưng chạy bằng JDK cũ hơn | Chạy bằng đúng JDK 21 (`mvn -v` phải là 21) |
| Tải dependency rất chậm | Lần đầu Maven tải toàn bộ thư viện về `~/.m2` | Bình thường (vài trăm MB). Chạy `mvn -q dependency:go-offline` một lần ở Phase 1 để tải trước |
| Công ty/trường có proxy | Maven không qua được proxy | Cấu hình `~/.m2/settings.xml` với `<proxies>` — nói tôi biết, tôi sẽ đưa file mẫu |

---

## 9. Nếu sau này bạn cài Docker (không bắt buộc)

Ở Phase 13 tôi vẫn tạo `docker-compose.yml` theo SDD §13. Khi đó bạn có thể chạy PostgreSQL bằng:
```bash
docker compose up -d db
```
và **không cần** cài PostgreSQL native. Khi chuyển qua lại giữa hai cách, chỉ cần đảm bảo:
* Cổng trùng nhau (mặc định `5432`) — không chạy cả hai cùng lúc hoặc đổi port một bên.
* Database/role giống nhau (`minisoc_db` / `minisoc`) để `DB_URL` không đổi.
* Dữ liệu là **khác nhau** (native dùng data directory của hệ điều hành, Docker dùng volume) ⇒ đừng bối rối khi thấy bảng "trống" sau khi đổi cách chạy.
