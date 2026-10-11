# Mini SOC Dashboard — E-Commerce Security Monitoring System

> Dự án môn **Lập trình Java nâng cao**: một website thương mại điện tử nhỏ (môi trường sinh security events)
> kết hợp một **Mini SOC** (thu thập – phát hiện – cảnh báo – tương quan – điều tra – dashboard realtime)
> và **Attack Simulator** để demo/kiểm thử.

**Stack:** Java 21 · Spring Boot 4.1.1 · Spring MVC · Spring Data JPA/Hibernate · Spring Security ·
PostgreSQL 18 · Flyway · Thymeleaf · WebSocket/STOMP · JUnit 5 + Mockito · SLF4J/Logback · Maven.

---

## 📌 Trạng thái dự án

| Phase | Nội dung | Trạng thái | Tài liệu |
|---|---|---|---|
| **0** | Project Planning | ✅ Hoàn thành | [docs/PHASE-0-PLANNING.md](docs/PHASE-0-PLANNING.md) |
| **1** | Project Initialization | ✅ Code xong — ⏳ chờ chạy `mvn` | [docs/PHASE-01-INITIALIZATION.md](docs/PHASE-01-INITIALIZATION.md) |
| **2** | Database (Flyway + JPA entities) | ✅ Code xong — ✅ 9/9 migration + 64/64 ràng buộc đã kiểm chứng trên PostgreSQL 18.3 — ⏳ chờ chạy `mvn test` | [docs/PHASE-02-DATABASE.md](docs/PHASE-02-DATABASE.md) |
| **3** | Authentication & Authorization (Spring Security, 3 role) | ✅ Code xong — ✅ gói/annotation đã đối chiếu với Boot 4.1.1 & Security 7.1.1 — ⏳ chờ `mvn test` + đăng nhập thử | [docs/PHASE-03-AUTHENTICATION.md](docs/PHASE-03-AUTHENTICATION.md) |
| 4–14 | E-Commerce → SOC → Realtime → Simulator → Tests → Docker → Docs | ⬜ Chưa bắt đầu | — |

Theo quy tắc SDD (§20): mỗi lần chỉ thực hiện **một phase**, kết thúc phase phải chạy/test, giải thích và **chờ xác nhận** trước khi sang phase tiếp theo.

---

## 🚀 QUICK START

### 0. Lấy code về máy (làm một lần)

```bash
git clone https://github.com/nghiamean-29/Mini-SOC-Dashboard-E-Commerce-Security-Monitoring-System.git
cd Mini-SOC-Dashboard-E-Commerce-Security-Monitoring-System
git checkout arena/01a0c33c-mini-soc-dashboard-e-commerce
```

**Cập nhật về sau:** `git pull --ff-only` (chi tiết + xử lý sự cố: **[docs/GIT-WORKFLOW.md](docs/GIT-WORKFLOW.md)**)

### ⚠️ Đọc trước: ở Phase 3 chạy được gì?

| Việc | Cần PostgreSQL? | Bằng chứng thành công |
|---|---|---|
| `mvn -q clean compile` | ❌ | exit code 0 |
| `mvn test` | ✅ **có** (`minisoc_test`) | `Tests run: 158, Failures: 0, Errors: 0` |
| `mvn -q clean package` | ❌ | có `target/mini-soc-dashboard-0.0.1-SNAPSHOT.jar` |
| `mvn spring-boot:run` | ✅ **có** | log `Started MiniSocApplication`; mở `http://localhost:8080/` thấy trang chủ |

**Đã có trang web**: `/` (trang chủ), `/login`, `/register`, và 3 trang lỗi (`403`, `404`, lỗi chung).
Vì có Spring Security, các URL khác (`/cart`, `/orders`, `/admin/**`, `/soc/**`) **yêu cầu đăng nhập
trước**; sau khi đăng nhập, `/admin/**` và `/soc/**` trả **403** nếu sai role, còn `/cart`, `/orders`
trả **404** vì giao diện thuộc Phase 4. Đó là kết quả đúng — bảng kiểm tra chi tiết ở
[docs/PHASE-03-AUTHENTICATION.md §5.4](docs/PHASE-03-AUTHENTICATION.md).

### Yêu cầu môi trường

| Thành phần | Version | Bắt buộc |
|---|---|---|
| JDK | 21 (LTS, Temurin) | ✅ |
| Maven | 3.9.16 | ✅ |
| PostgreSQL | 18.x (tối thiểu 14) | ✅ khi chạy ứng dụng |
| Docker | — | ⬜ không cần (chỉ Phase 13) |

Hướng dẫn cài đặt chi tiết (Windows / macOS / Linux): **[docs/ENVIRONMENT-SETUP.md](docs/ENVIRONMENT-SETUP.md)**

### Tạo database (một lần)

```bash
psql -U postgres -c "CREATE ROLE minisoc LOGIN PASSWORD 'matkhau-cua-ban';"
psql -U postgres -c "CREATE DATABASE minisoc_db OWNER minisoc ENCODING 'UTF8';"
# Từ Phase 2: thêm database cho kiểm thử (mvn test chạy trên PostgreSQL thật, không dùng H2)
psql -U postgres -c "CREATE DATABASE minisoc_test OWNER minisoc ENCODING 'UTF8';"
```

### Build và test

```bash
mvn -q clean compile     # không cần database
export DB_PASSWORD='matkhau-cua-ban'   # Windows: $env:DB_PASSWORD="..."
mvn test                 # Phase 1+2: 73 test | Phase 3: + 8 lớp / 85 test (158 tổng)
                         # (cần database minisoc_test — xem mục Tạo database ở trên)
mvn -q clean package     # tạo target/mini-soc-dashboard-0.0.1-SNAPSHOT.jar
```

### Kiểm chứng lớp SQL (không cần PostgreSQL, không cần Maven)

```bash
cd tools/sql-validation
npm install              # tải PGlite 0.5.8 (PostgreSQL 18.3 qua WASM)
node validate.mjs        # 9/9 migration + 64/64 check PASS
```

### Chạy ứng dụng (cần PostgreSQL)

```bash
# Linux / macOS
export DB_PASSWORD='matkhau-cua-ban'
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run

# Windows PowerShell
$env:DB_PASSWORD="matkhau-cua-ban"; $env:SPRING_PROFILES_ACTIVE="dev"
mvn spring-boot:run
```

Mở <http://localhost:8080/> → trang chủ. Đăng ký ở `/register`, đăng nhập ở `/login`.

> 🔐 Muốn có tài khoản **ADMIN** đầu tiên (để gán role cho người khác), đặt thêm biến môi trường
> `APP_BOOTSTRAP_ADMIN_USERNAME`, `APP_BOOTSTRAP_ADMIN_PASSWORD`, `APP_BOOTSTRAP_ADMIN_EMAIL`,
> `APP_BOOTSTRAP_ADMIN_FULL_NAME` rồi chạy lại — hướng dẫn đầy đủ ở
> [docs/PHASE-03-AUTHENTICATION.md §5.2](docs/PHASE-03-AUTHENTICATION.md).
> Cách gán role `SOC_ANALYST` bằng SQL: cùng tài liệu, §5.3.

Kiểm tra sức khoẻ (chỉ ADMIN, cần đăng nhập trước): <http://localhost:8080/actuator/health> → `{"status":"UP"}`

> ℹ️ **Secret không bao giờ nằm trong repo.** Cấu hình đọc từ biến môi trường
> (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) — xem `config/env/dev.env.example`.
> Profile `prod` **không có** giá trị mặc định cho credential: thiếu biến ⇒ ứng dụng dừng ngay (fail fast).

---

## 🗂️ Tài liệu

| Tài liệu | Nội dung |
|---|---|
| [docs/PHASE-0-PLANNING.md](docs/PHASE-0-PLANNING.md) | Requirements, Actors, Use Cases, Architecture, Database overview, Security architecture, Roadmap, Risks, ADR |
| [docs/PHASE-01-INITIALIZATION.md](docs/PHASE-01-INITIALIZATION.md) | Giải thích `pom.xml`, cấu hình, cấu trúc package, test; 6 khác biệt của Spring Boot 4; cách kiểm chứng |
| [docs/PHASE-02-DATABASE.md](docs/PHASE-02-DATABASE.md) | 9 migration Flyway, 17 bảng, 14 entity + 14 repository, 15 quyết định thiết kế, bằng chứng 64/64 check trên PostgreSQL 18.3, cách chạy `mvn test` |
| [docs/PHASE-03-AUTHENTICATION.md](docs/PHASE-03-AUTHENTICATION.md) | Spring Security: đăng ký/đăng nhập/đăng xuất, ma trận phân quyền 3 role, 17 quyết định thiết kế, tạo ADMIN đầu tiên, SQL gán role, kịch bản kiểm tra bằng tay, FAQ |
| [docs/ENVIRONMENT-SETUP.md](docs/ENVIRONMENT-SETUP.md) | Cài JDK/Maven/PostgreSQL, tạo DB theo least privilege, biến môi trường, xử lý sự cố |
| [docs/GIT-WORKFLOW.md](docs/GIT-WORKFLOW.md) | **Cách lấy/cập nhật code về máy**: clone, `git pull`, xử lý conflict, nhánh `main` để nộp bài |

`ARCHITECTURE.md`, `DATABASE.md`, `SECURITY.md`, `API.md` sẽ được tạo ở **Phase 14** (theo SDD §14).

---

## 🧱 Cấu trúc project hiện tại (Phase 1–2)

```
src/main/java/com/yak/minisoc/
├── MiniSocApplication.java      # @SpringBootApplication (entry point)
├── entity/                       # PHASE 2: 3 base class + 14 entity + 11 enum (security/ cho SOC)
├── repository/                   # PHASE 2: 14 interface Spring Data JPA
├── util/BusinessCodes.java       # PHASE 2: mã ORD-yyyyMMdd-NNNNNN và INC-0001
├── config/                       # PHASE 3: SecurityConfig, BootstrapAdminProperties, AdminBootstrapConfig
├── controller/                   # PHASE 3: HomeController, AuthController
├── dto/                          # PHASE 3: RegisterRequest
├── service/                      # PHASE 3: AuthService
├── security/                     # PHASE 3: AppRoles, AuthenticatedUser, DatabaseUserDetailsService
├── validation/                   # PHASE 3: @PasswordMatches (+ validator)
├── exception/                    # PHASE 3: BusinessException, DuplicateAccountException
├── detection/      alert/         incident/     audit/      simulator/
├── mapper/         websocket/
└──      ↑ các package này sẽ được lấp đầy ở các phase sau (detection = Phase 6, simulator = Phase 13…)

src/main/resources/
├── application.yml          # cấu hình chung (secret + bootstrap ADMIN đọc từ ENV)
├── application-dev.yml      # log SQL, template cache off, simulator bật
├── application-prod.yml     # không có default cho credential, simulator tắt
├── db/migration/            # PHASE 2: V1..V9 - nguồn sự thật của schema
├── templates/               # PHASE 3: fragments/layout, index, login, register, error/{403,404,error}
└── static/{css,js,vendor}/  # PHASE 3: app.css (tự viết, không framework/CSS ngoài) + favicon.svg

src/test/
├── resources/application-test.yml   # PHASE 2: profile test (PostgreSQL minisoc_test)
└── java/…/                          # PHASE 2: 6 lớp @DataJpaTest
                                     # PHASE 3: + 8 lớp (SecurityConfigTest, ErrorPageRenderingTest, …)

tools/sql-validation/                # PHASE 2: harness kiểm chứng SQL trên PostgreSQL 18.3
└── validate.mjs                     # node validate.mjs → 64/64 check
```

---

## 🎯 Luồng demo mục tiêu (khi hoàn thành)

```
Attack Simulator → Brute Force → nhiều LOGIN_FAILED
    → Detection Engine (BruteForceRule) → Alert BRUTE_FORCE / HIGH
    → SOC Dashboard realtime (WebSocket/STOMP)
    → Incident #INC-0001 → SOC Analyst điều tra → Incident Resolved
```

## ⚖️ Lưu ý đạo đức

Module **Attack Simulator** chỉ **sinh dữ liệu mô phỏng bên trong ứng dụng** để phục vụ demo và kiểm thử.
Dự án **không** thực hiện tấn công vào bất kỳ hệ thống bên ngoài nào.
