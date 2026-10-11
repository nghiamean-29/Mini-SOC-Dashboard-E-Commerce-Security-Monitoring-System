# PHASE 1 — PROJECT INITIALIZATION

| | |
|---|---|
| **Document ID** | `PHASE-01-INITIALIZATION` |
| **Ngày** | 2026-09-21 |
| **Trạng thái** | ✅ Code xong — ⏳ **chờ bạn chạy `mvn` và xác nhận** |
| **Tiền đề** | Phase 0 đã được duyệt (D-1..D-8 đã chốt) |
| **Phase trước** | [PHASE-0-PLANNING.md](PHASE-0-PLANNING.md) |
| **Phase sau** | Phase 2 — Database (Flyway + JPA entity) |

---

## 0. GATE CỦA PHASE 1 (điều kiện để sang Phase 2)

> **`mvn -q clean compile` chạy thành công (BUILD SUCCESS).**

Phase 1 **cố tình không có** entity, controller hay business logic. Mục tiêu duy nhất là: *một bộ khung project biên dịch được, cấu hình đúng, có kiểm thử, và chạy được khi PostgreSQL sẵn sàng.*

---

## 1. NHỮNG GÌ ĐÃ ĐƯỢC TẠO

```
Mini-SOC-Dashboard-E-Commerce-Security-Monitoring-System/
│
├── pom.xml                              ← "bản thiết kế" của project (Maven)
├── .gitignore                           ← chặn build output + SECRET khỏi git
├── .editorconfig                        ← thống nhất format giữa các editor
├── README.md                            ← cập nhật trạng thái + quick start
│
├── config/env/
│   └── dev.env.example                  ← mẫu biến môi trường (KHÔNG chứa secret thật)
│
├── src/main/java/com/yak/minisoc/
│   ├── MiniSocApplication.java          ← điểm khởi động duy nhất
│   └── 17 × <package>/package-info.java ← bản đồ kiến trúc: mỗi package 1 nhiệm vụ
│
├── src/main/resources/
│   ├── application.yml                  ← cấu hình dùng chung mọi profile
│   ├── application-dev.yml              ← profile dev (học tập: log SQL, xem chi tiết)
│   ├── application-prod.yml             ← profile prod (không có secret mặc định)
│   ├── db/migration/                    ← Flyway sẽ đọc migration ở đây (Phase 2)
│   ├── templates/                        ← Thymeleaf (Phase 3+)
│   └── static/{css,js,vendor}/           ← CSS + JS thuần (Phase 3+; vendor = STOMP client Phase 10)
│
└── src/test/java/com/yak/minisoc/
    ├── MiniSocApplicationTest.java       ← kiểm tra "hợp đồng" của class main
    └── config/ConfigurationFilesTest.java← kiểm tra chính các file cấu hình
```

---

## 2. SÁU PHÁT HIỆN VỀ SPRING BOOT 4 (đã kiểm tra thực tế, không đoán)

Trước khi viết `pom.xml`, tôi đã đối chiếu **source code của Spring Boot 4.1.1** (chứ không dựa vào trí nhớ hay tài liệu Boot 3.x). Sáu điểm dưới đây là những chỗ **nếu đoán theo Boot 3 sẽ viết sai**:

| # | Phát hiện | Hệ quả thực tế | Tôi xử lý thế nào |
|---|---|---|---|
| 1 | `spring-boot-starter-web` bị **deprecated**, đổi tên thành **`spring-boot-starter-webmvc`** | Tutorial Boot 3 dạy tên cũ; dùng tên cũ vẫn chạy nhưng đã deprecated | Dùng `spring-boot-starter-webmvc` (nội dung tương đương: MVC + Tomcat + Jackson + HTTP converter) |
| 2 | Boot 4 có **`spring-boot-starter-flyway`** mới | Trước đây phải tự thêm `flyway-core` + `flyway-database-postgresql` | Dùng starter mới, nhưng **vẫn** phải thêm 1 artifact (xem #3) |
| 3 | `flyway-database-postgresql` là **optional** trong module `spring-boot-flyway` | **Optional = KHÔNG được kéo theo tự động.** Thiếu nó ⇒ Flyway lỗi `Unsupported Database: PostgreSQL` khi khởi động (đúng lúc Phase 2) | Khai báo tường minh `<artifactId>flyway-database-postgresql</artifactId>` với comment giải thích lý do |
| 4 | Boot 4 dùng **Jackson 3**, package đổi từ `com.fasterxml.jackson` → **`tools.jackson`** | Code Jackson viết theo ví dụ cũ sẽ không biên dịch | Phase 1 chưa dùng Jackson trực tiếp; sẽ dùng API Jackson 3 khi cần (Phase 7+) |
| 5 | `spring-boot-starter-test` nay bao gồm sẵn **Awaitility** | Trước đây phải thêm thủ công để test bất đồng bộ | Dùng Awaitility ở Phase 5/10 để test event pipeline **không dùng `Thread.sleep`** (giảm flaky test — R-11) |
| 6 | `spring-boot-starter-parent` lọc resource `application*.yml` bằng delimiter **`@...@`** (không phải `${...}`) | Nếu dùng `${...}` thì Maven sẽ *nuốt* các placeholder `${DB_URL}` lúc build ⇒ cấu hình hỏng ngay lần build đầu | ✅ Điều này **thực sự cần thiết**: ta có `${DB_URL}` (cho runtime) và `@project.version@` (cho Maven) — hai cơ chế khác nhau, không được trùng delimiter |

Chi tiết #6 là bài học đáng nhớ nhất của Phase 1:

```yaml
# Trong application.yml, HAI loại placeholder cùng tồn tại:

datasource:
  url: ${DB_URL:jdbc:postgresql://localhost:5432/minisoc_db}   # ← Runtime: Spring đọc biến môi trường
info:
  app:
    version: '@project.version@'                                # ← Build time: Maven thay bằng 0.0.1-SNAPSHOT
```
* `${...}` → do **Spring** xử lý **lúc chạy** (runtime).
* `@...@` → do **Maven** xử lý **lúc build** (resource filtering).
* Vì Maven chỉ lọc `@...@`, các `${...}` của bạn **đi nguyên vẹn** vào file trong `target/classes`. Nếu delimiter là `${}` thì Maven đã xoá/thay hết chúng ⇒ ứng dụng mất hết cấu hình.

---

## 3. GIẢI THÍCH TỪNG FILE (theo 7 bước của SDD §19)

### 3.1. `pom.xml` — mô tả project với Maven

| Câu hỏi | Trả lời |
|---|---|
| **Mục đích** | Khai báo project: dùng Java version nào, thư viện gì, đóng gói thế nào. Maven đọc file này để tải thư viện và biên dịch. |
| **Layer** | Không thuộc layer nào — đây là file **build**, không phải code nghiệp vụ. |
| **Dependency** | `spring-boot-starter-parent` (parent POM) quản lý version cho mọi thứ bên dưới. |
| **Java concept** | Không có (XML), nhưng là nơi quyết định **classpath** — vì vậy nó quyết định Spring Boot auto-config cấu hình cái gì. |
| **Tại sao cách này** | Dùng parent POM thay vì tự khai báo version cho từng thư viện: tránh "dependency hell" (mỗi thư viện một version không tương thích nhau). |

**Toàn bộ dependency và lý do tồn tại:**

| Dependency | Scope | Vì sao cần (cho phase nào) |
|---|---|---|
| `spring-boot-starter-webmvc` | compile | Spring MVC + Tomcat + Jackson: REST + trang Thymeleaf (Phase 3–9) |
| `spring-boot-starter-thymeleaf` | compile | Render HTML phía server (Phase 3+) |
| `spring-boot-starter-websocket` | compile | Đẩy dữ liệu realtime lên dashboard (Phase 10) |
| `spring-boot-starter-security` | compile | Xác thực, phân quyền, BCrypt, CSRF (Phase 3) |
| `spring-boot-starter-validation` | compile | `@Valid`, `@NotBlank`… validate input ở biên (Phase 3+) |
| `spring-boot-starter-data-jpa` | compile | Spring Data JPA + Hibernate + HikariCP (Phase 2+) |
| `spring-boot-starter-flyway` | compile | Quản lý schema bằng migration (Phase 2) |
| `flyway-database-postgresql` | **runtime** | Hỗ trợ PostgreSQL cho Flyway 12 — **bắt buộc** (xem §2 #3) |
| `postgresql` (driver) | **runtime** | JDBC driver. **`runtime`** vì code của ta chỉ dùng JDBC API/JPA, không `import org.postgresql.*` |
| `spring-boot-starter-actuator` | compile | `/actuator/health` để kiểm tra DB ngay trong Phase 1 (Phase 13 mở rộng) |
| `spring-boot-starter-test` | **test** | JUnit 5 + Mockito + AssertJ + Awaitility. Chỉ có trên classpath khi test ⇒ không nằm trong file `.jar` chạy thật |

> **Điểm cần hiểu — scope `runtime` vs `compile`:** dependency `runtime` vẫn có mặt khi chạy nhưng **không** có khi biên dịch. Nếu ta vô tình viết `import org.postgresql.Driver` trong code thì build sẽ **fail ngay** → đó là điều tốt: nó giữ cho code không bị "khoá cứng" vào một database cụ thể.

> **Điểm cần hiểu — tại sao KHÔNG khai báo 3 test starter còn lại ngay bây giờ?** Vì `spring-boot-starter-webmvc-test`, `-security-test`, `-data-jpa-test` chưa được dùng ở Phase 1. Dependency không dùng là "noise" — người đọc không biết cái nào thật sự cần. Tôi đã ghi rõ trong `pom.xml` phase nào sẽ thêm cái nào.

### 3.2. `MiniSocApplication.java` — điểm khởi động

| Câu hỏi | Trả lời |
|---|---|
| **Mục đích** | Bật Spring container. Không chứa logic. |
| **Layer** | Root (không thuộc layer nào) — nó ở gốc để **quyết định phạm vi component scan**. |
| **Dependency** | Chỉ `org.springframework.boot.*`. |
| **Java concept** | Annotation, `static` method, reflection (Spring dùng reflection để tìm class có `@SpringBootApplication`). |
| **Tại sao cách này** | `@SpringBootApplication` = 3 annotation gộp lại: `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`. Việc đặt class ở gốc `com.yak.minisoc` khiến **mọi** package con được scan tự động, không thể quên. |

```java
@SpringBootApplication
public class MiniSocApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniSocApplication.class, args);
    }
}
```

**`@EnableAutoConfiguration` hoạt động thế nào (giải thích để hiểu, không học vẹt):** lúc khởi động, Spring Boot đọc danh sách ~150 class auto-configuration có trong `spring-boot-autoconfigure`. Mỗi class có điều kiện (`@ConditionalOnClass`, `@ConditionalOnMissingBean`…). Vì `pom.xml` có `spring-boot-starter-webmvc` → trên classpath có `DispatcherServlet` → auto-config bật Spring MVC và Tomcat. Vì có `data-jpa` → nó tạo `EntityManagerFactory`, `DataSource`… **Đây là lý do `pom.xml` và `application.yml` phải khớp nhau:** thêm starter ⇒ Spring tự cấu hình thêm thành phần ⇒ cần thêm cấu hình tương ứng.

### 3.3. `application.yml` + 2 profile file — cấu hình theo môi trường

| Câu hỏi | Trả lời |
|---|---|
| **Mục đích** | Khai báo cấu hình tách khỏi code: URL database, session, ngưỡng detection… |
| **Layer** | Infrastructure. |
| **Dependency** | Được Spring `Environment` đọc; binding vào `@ConfigurationProperties` (Phase 6). |
| **Java concept** | Profile, property placeholder resolution, externalized configuration. |
| **Tại sao cách này** | **Không hard-code** (NFR-SEC-02): secret đến từ ENV; ngưỡng detection cấu hình được (FR-DET-07). |

**Profile là gì?** Một cơ chế để có nhiều bộ cấu hình trong cùng một artifact: `application.yml` (chung) + `application-dev.yml` (khi `SPRING_PROFILES_ACTIVE=dev`) + `application-prod.yml` (khi chạy production). Spring đọc file chung trước, rồi **ghi đè** bằng file profile đang hoạt động. Nhờ vậy cùng một file `.jar` chạy được ở cả hai môi trường mà không cần build lại.

**Ba file làm gì:**

| File | Nội dung then chốt | Vì sao |
|---|---|---|
| `application.yml` | datasource (có default cho dev), JPA `ddl-auto: validate`, `open-in-view: false`, `time_zone: UTC`, Flyway `classpath:db/migration`, session cookie `HttpOnly` + `SameSite=Lax`, error page không lộ stack trace, ngưỡng detection, actuator `health,info`, `info.app.*` | Cấu hình **an toàn mặc định** cho mọi môi trường |
| `application-dev.yml` | `thymeleaf.cache: false`, `logging.level.com.yak.minisoc: DEBUG`, `org.hibernate.SQL: DEBUG`, `app.simulator.enabled: true` | Nhìn thấy nhiều nhất khi học. Các dòng log **nguy hiểm** (bind parameters) được **comment sẵn** kèm cảnh báo |
| `application-prod.yml` | `${DB_URL}` **không có default**, cookie `secure: true`, simulator `false`, log `WARN`, actuator chỉ `health` | Fail fast khi thiếu secret; tắt mọi thứ phục vụ demo |

Ba quyết định đáng chú ý trong cấu hình:

1. **`ddl-auto: validate`** — Hibernate **không** được tự tạo/sửa bảng. Schema do Flyway quản lý. Vì sao: trong dự án bảo mật, mọi thay đổi schema phải là một file migration **review được, có version, có trong git**. `ddl-auto: update` biến schema thành thứ "tự diễn biến", không ai biết bảng nào được thêm lúc nào.
2. **`open-in-view: false`** — mặc định của Spring Boot là `true` (giữ session Hibernate mở suốt quá trình render Thymeleaf). Ta tắt để **LazyInitializationException nổ ngay khi code sai**, thay vì âm thầm sinh hàng trăm truy vấn phụ khi render (`N+1 query`). Đây là một trong những quyết định ảnh hưởng lớn nhất tới chất lượng code Phase 4.
3. **`hibernate.jdbc.time_zone: UTC` + `TIMESTAMPTZ`** (NFR-DATA-01) — hệ thống bảo mật so sánh "5 lần fail trong 60 giây". Nếu thời gian không nhất quán, cửa sổ detection sai. Lưu UTC, chỉ đổi sang `Asia/Ho_Chi_Minh` khi hiển thị.

### 3.4. 17 × `package-info.java` — bản đồ kiến trúc

Mỗi package có một file Javadoc giải thích 5 điều: **layer**, **trách nhiệm**, **được đặt gì vào**, **KHÔNG được đặt gì**, **class nào sẽ có ở phase nào**.

**Vì sao làm việc này ngay từ đầu?**

* Kiến trúc trong tài liệu Phase 0 rất dễ "trôi" khi bắt tay viết code. `package-info.java` là **hợp đồng ngay tại chỗ**: ai mở `service/` sẽ đọc được "controller không được gọi từ đây".
* Nó biến quy tắc §8.1 của Phase 0 (R1–R4) thành thứ **nhìn thấy được**, chứ không chỉ nằm trong đầu.
* `package-info.java` là cú pháp **Javadoc cho package** — một kỹ thuật Java ít người mới biết, và hoàn toàn hợp lệ để biên dịch.

Dưới đây là điểm khác biệt quan trọng nhất mà tôi đã ghi rõ trong đó — **hai nghĩa của chữ "security"** trong dự án:

```
com.yak.minisoc.security    → BẢO VỆ chính ứng dụng (Spring Security: đăng nhập, phân quyền)
com.yak.minisoc.detection   → PHÁT HIỆN tấn công nhắm vào ứng dụng (rule, alert)
```
Hai việc hoàn toàn khác nhau. Gộp chúng vào một package là cách chắc chắn nhất để tạo ra một package rối không ai dám sửa.

### 3.5. Hai test của Phase 1 — kiểm tra những thứ build không kiểm tra được

Cả hai test **không cần database và không khởi động Spring**, nên bạn chạy được ngay cả khi PostgreSQL chưa cài.

**`MiniSocApplicationTest`** — bảo vệ "hợp đồng" của class main:

| Assertion | Nếu bị vi phạm thì hậu quả là gì |
|---|---|
| Có `@SpringBootApplication` | Không có ⇒ app không khởi động được |
| `scanBasePackages` rỗng | Có người thêm `scanBasePackages = "com.yak.minisoc.controller"` ⇒ `service`, `detection`… **không được scan** ⇒ lỗi "No qualifying bean" rất khó tìm |
| `main` là `public static void String[]` | Sai chữ ký ⇒ `java -jar` báo "no main manifest attribute" |

**`ConfigurationFilesTest`** — kiểm tra **chính các file cấu hình** bằng đúng class mà Spring Boot dùng (`YamlPropertySourceLoader`). Ý tưởng: một lỗi typo trong YAML (`thiếu dấu hai chấm`, thụt lề sai, viết mật khẩu thật) thường chỉ lộ ra khi ứng dụng **không khởi động được** — thời điểm tệ nhất để phát hiện. Test này biến nó thành lỗi build:

| Test | Bảo vệ điều gì |
|---|---|
| `everyConfigurationFileIsValidYaml` | YAML hợp lệ, không rỗng |
| `declaresApplicationName` | Tên app dùng cho log/metrics (Phase 13) |
| `credentialsComeFromEnvironmentVariables` | URL/user/password đều là placeholder |
| `placeholderResolutionUsesEnvironmentValues` | Cơ chế `${VAR:default}` hoạt động đúng: có ENV ⇒ lấy ENV; không có ⇒ lấy default; không có cả hai ⇒ chuỗi rỗng (không phải lỗi) |
| **`noPlaintextSecretIsCommitted`** | **Duyệt mọi property: key nào chứa `password/secret/token/credential/api-key` thì giá trị BẮT BUỘC phải là `${...}` hoặc rỗng.** Đây là "cảnh báo sớm" cho NFR-SEC-02 — nó chạy mỗi lần `mvn test` |
| `productionProfileHasNoCredentialDefaults` | Profile prod **không được** có default cho credential (phải fail fast) |
| `simulatorSwitchIsProfileControlled` | Simulator: tắt mặc định, bật ở dev, tắt ở prod |
| `schemaAndTimeZoneSettingsAreExplicit` | `ddl-auto=validate`, `open-in-view=false`, `time_zone=UTC` — những cấu hình nếu bị đổi sẽ gây lỗi rất khó truy (schema lệch, giờ sai) |

**Java concept dùng trong test:** `YamlPropertySourceLoader` (Spring Boot API), `MockEnvironment` (spring-test), **`List#getFirst()` — API mới của Java 21** (SequencedCollection, JEP 431). Nếu bạn chạy trên Java 17 thì dòng này sẽ không biên dịch — đó cũng là một cách "khoá" phiên bản Java vào build.

### 3.6. `.gitignore`, `.editorconfig`, `config/env/dev.env.example`

* **`.gitignore`**: chặn `target/`, file IDE, log, và **quan trọng nhất** `config/env/*.env` (chứa mật khẩu thật) nhưng vẫn cho phép `*.example`. Nguyên tắc: *file mẫu được commit, file thật thì không.*
* **`.editorconfig`**: thống nhất indent (4 space cho Java, 2 cho YAML/HTML), line-ending LF. Tránh diff "sửa 400 dòng" chỉ vì IDE đổi khoảng trắng.
* **`config/env/dev.env.example`**: mẫu để copy thành `dev.env`. Giá trị là giả (`change-me-before-use`) và có hướng dẫn cho cả Linux/macOS/Windows PowerShell.

---

## 4. KIẾN THỨC MỚI CỦA PHASE 1 (theo 4 câu hỏi của SDD §21)

| Khái niệm | Là gì? | Vì sao project cần? | Hoạt động thế nào? | Nằm ở đâu trong kiến trúc? |
|---|---|---|---|---|
| **Maven** | Công cụ build + quản lý thư viện của Java | Không ai tự tải 60 file `.jar` bằng tay | Đọc `pom.xml` → tải dependency từ Maven Central về `~/.m2` → chạy vòng đời `compile → test → package` | Ngoài ứng dụng (build time) |
| **Parent POM / BOM** | "Bảng giá" các version đã được kiểm chứng tương thích | Tránh xung đột version giữa Spring/Hibernate/Jackson/Flyway | Khai báo `<parent>` ⇒ mọi dependency con **không cần ghi version** | `pom.xml` |
| **Dependency scope** | Phạm vi classpath của một thư viện | Giữ jar chạy thật nhỏ và ngăn code phụ thuộc sai tầng | `compile` (mặc định) / `runtime` (không biên dịch được) / `test` (chỉ khi test) | `pom.xml` |
| **Spring Boot Starter** | Gói dependency theo "tính năng" | Thay vì liệt kê 8 jar, chỉ khai báo `spring-boot-starter-data-jpa` | Là POM rỗng chỉ chứa danh sách dependency liên quan | `pom.xml` |
| **Annotation** | Siêu dữ liệu gắn lên class/method, đọc được lúc chạy qua reflection | Spring đọc annotation để biết phải cấu hình gì | `@SpringBootApplication` ⇒ Spring dùng reflection phát hiện và xử lý | Rải rác trong toàn bộ code Spring |
| **IoC Container / DI** | Spring tự tạo object và tiêm phụ thuộc vào object khác | Không tự `new` khắp nơi ⇒ dễ test, dễ thay thế | Lúc khởi động, Spring scan class có `@Component/@Service/...`, tạo "bean" và tiêm qua constructor | `MiniSocApplication` khởi động nó; mọi layer dùng |
| **Auto-configuration** | Spring tự cấu hình theo classpath | Giảm cấu hình thủ công, nhưng phải hiểu để debug | Đọc điều kiện `@ConditionalOnClass`…; có starter nào thì bật tính năng đó | Trong `spring-boot-autoconfigure`, kích hoạt từ `MiniSocApplication` |
| **Profile** | Bộ cấu hình theo môi trường | Dev cần log SQL, prod thì không | `SPRING_PROFILES_ACTIVE=dev` ⇒ Spring nạp `application-dev.yml` lên trên file chung | `src/main/resources/application*.yml` |
| **Externalized configuration** | Cấu hình nằm **ngoài** code (ENV/file) | Không commit secret; đổi cấu hình không cần build lại | Placeholder `${VAR:default}` được `Environment` resolve khi khởi động | `application.yml` + ENV |
| **Resource filtering** | Maven thay placeholder trong file resource lúc build | Đưa version/nội dung build vào file cấu hình | Delimiter `@...@` (Boot 4) được Maven thay | `pom.xml` + `application.yml` |
| **Flyway migration** | Quản lý thay đổi schema bằng file SQL có version | Schema phải tái lập được, review được, có lịch sử | Lúc khởi động Flyway đọc `db/migration`, so với bảng `flyway_schema_history` rồi áp dụng file còn thiếu | Chạy **trước** khi Hibernate khởi tạo |
| **Actuator** | Endpoint vận hành (`/actuator/health`) | Biết ngay DB có kết nối được không, không cần viết UI | Spring Boot tự thêm endpoint; bảo mật ở Phase 3 | Tầng vận hành |

---

## 5. CHẠY VÀ KIỂM CHỨNG

### 5.1. Bước 1 — Build (GATE của Phase 1, **không cần database**)

```bash
mvn -q clean compile
```
**Kết quả mong đợi:** không có output, exit code 0. Chạy lại không dùng `-q` để thấy `BUILD SUCCESS`.

Lần đầu Maven sẽ tải khá nhiều (khoảng 150–250 MB vào `~/.m2`). Nếu muốn tải trước cho chắc:
```bash
mvn -q dependency:go-offline
```

### 5.2. Bước 2 — Chạy test (**không cần database**)

```bash
mvn test
```
**Kết quả mong đợi (rút gọn):**
```
[INFO] Running com.yak.minisoc.MiniSocApplicationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.yak.minisoc.config.ConfigurationFilesTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
Tổng: **11 test, 0 failure**. Nếu có test nào fail, phần message sẽ nói rõ file/property nào sai — hãy dán cho tôi.

### 5.3. Bước 3 — Đóng gói

```bash
mvn -q clean package
```
**Kiểm tra file jar có "chạy được" không:**
```bash
unzip -l target/mini-soc-dashboard-0.0.1-SNAPSHOT.jar | head -20        # Linux/macOS
# Windows PowerShell: tar -tf target\mini-soc-dashboard-0.0.1-SNAPSHOT.jar | Select-Object -First 20
```
Bạn phải thấy `BOOT-INF/classes/com/yak/minisoc/MiniSocApplication.class` và `BOOT-INF/lib/` chứa các jar. **`BOOT-INF/` là dấu hiệu jar đã được `spring-boot-maven-plugin` đóng gói lại** (executable jar).

### 5.4. Bước 4 — Khởi động ứng dụng (**cần PostgreSQL đã sẵn sàng**)

> ⚠️ **Đây là điểm cần hiểu rõ:** vì có `data-jpa` + `flyway` trên classpath, ứng dụng **bắt buộc** kết nối được PostgreSQL mới khởi động. Chưa cài PostgreSQL thì cứ dừng ở Bước 1–3 — gate của Phase 1 đã đạt. (Đây cũng là "hợp đồng" của kiến trúc: database là hạ tầng bắt buộc, không phải tuỳ chọn.)

Chuẩn bị (xem [ENVIRONMENT-SETUP.md](ENVIRONMENT-SETUP.md) nếu chưa làm):
```bash
# 1) Tạo database + role (một lần)
psql -U postgres -c "CREATE ROLE minisoc LOGIN PASSWORD 'matkhau-cua-ban';"
psql -U postgres -c "CREATE DATABASE minisoc_db OWNER minisoc ENCODING 'UTF8';"

# 2) Nạp biến môi trường
#    Linux/macOS:
export DB_PASSWORD='matkhau-cua-ban'
export SPRING_PROFILES_ACTIVE=dev
#    Windows PowerShell:
#    $env:DB_PASSWORD="matkhau-cua-ban"; $env:SPRING_PROFILES_ACTIVE="dev"
```
Chạy:
```bash
mvn spring-boot:run
```

**Log mong đợi (những dòng quan trọng nhất):**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v4.1.1)

... HikariPool-1 - Starting...
... HikariPool-1 - Start completed.
... Flyway Community Edition ... by Redgate
... Successfully validated 0 migrations          ← chưa có migration nào (Phase 2 sẽ có)
... Current version of schema "public": << Empty Schema >>
... Using generated security password: 8f2c... (một UUID ngẫu nhiên)
... Tomcat started on port 8080 (http)
... Started MiniSocApplication in 3.5 seconds (process running for 4.2)
```

**Giải thích những dòng quan trọng:**

* `Successfully validated 0 migrations` là **bình thường** ở Phase 1: thư mục `db/migration` mới chỉ có `.gitkeep`. Nếu Flyway báo lỗi `Unsupported Database: PostgreSQL` → thiếu dependency `flyway-database-postgresql` (xem §2 #3).
* **`Using generated security password: <uuid>`** — dòng này làm nhiều bạn tưởng có gì sai. Nó xuất hiện vì Phase 1 **đã có** `spring-boot-starter-security` trên classpath mà **chưa có** `SecurityConfig`: Spring Security tự bật một cấu hình mặc định gồm HTTP Basic + form login, và tạo một user là `user` với mật khẩu ngẫu nhiên chỉ tồn tại trong bộ nhớ (in ra log để bạn đăng nhập thử được). Ở **Phase 3** ta thay hoàn toàn bằng xác thực đọc từ PostgreSQL (BCrypt) và dòng mật khẩu tạm này sẽ biến mất. → Đây là ví dụ cho thấy **auto-configuration chỉ là mặc định, không phải quyết định của ta**; và mặc định đó phải được thay đúng lúc.
* `Started MiniSocApplication` ⇒ container khởi động thành công, mọi cấu hình hợp lệ.

### 5.5. Bước 5 — Kiểm tra ứng dụng đang chạy

Mở terminal khác:
```bash
curl http://localhost:8080/actuator/health
```
**Kết quả mong đợi:**
```json
{"status":"UP"}
```
`"status":"UP"` **bao gồm cả health check của database** (Spring Boot tự kiểm tra `DataSource`) ⇒ đây là bằng chứng ứng dụng đã kết nối PostgreSQL thành công. (Tuỳ phiên bản/thiết lập, response có thể kèm thêm trường `groups` — điều quan trọng là `status` = `UP`.)

> **Vì sao endpoint này truy cập được dù chưa viết `SecurityConfig`?** `ManagementWebSecurityAutoConfiguration` của Spring Boot tự cho phép `health` và `info` khi ứng dụng **chưa** định nghĩa `SecurityFilterChain` riêng. Ở Phase 3 ta sẽ tự cấu hình lại và giới hạn `/actuator/**` cho role `ADMIN` (theo ma trận quyền ở Phase 0 §11.2).

```bash
curl http://localhost:8080/actuator/info
```
```json
{"app":{"name":"Mini SOC Dashboard","version":"0.0.1-SNAPSHOT","description":"..."}}
```
`version` xuất hiện nghĩa là **Maven resource filtering hoạt động** (delimiter `@...@`). 

Truy cập `http://localhost:8080/` sẽ thấy **404** — đúng như mong đợi vì Phase 1 chưa có controller nào. (404 này sẽ biến thành trang chủ ở Phase 4.)

### 5.6. Bước 6 — Kiểm tra database đã được Flyway "nhận"

```bash
psql -h localhost -U minisoc -d minisoc_db -c "\dt"
```
**Kết quả mong đợi ở Phase 1:** có thể là `Did not find any relations` (chưa có bảng nào) hoặc chỉ có `flyway_schema_history` — cả hai đều **bình thường**, vì thư mục migration đang rỗng. Từ Phase 2, lệnh này sẽ liệt kê đủ **16 bảng** + `flyway_schema_history` với 8 dòng tương ứng 8 file migration.

Dừng ứng dụng: `Ctrl + C` trong terminal đang chạy.

---

## 6. XỬ LÝ SỰ CỐ THƯỜNG GẶP Ở PHASE 1

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `UnsupportedClassVersionError: class file version 65.0` | Chạy bằng JDK cũ (< 21) | `mvn -v` phải hiện `Java version: 21.x`; sửa `JAVA_HOME` |
| `Could not resolve placeholder 'DB_URL'` | Chạy profile `prod` nhưng thiếu biến môi trường | Đây là **hành vi đúng** (fail fast). Set `DB_URL/DB_USERNAME/DB_PASSWORD` hoặc dùng profile `dev` |
| `FlywayException: Unsupported Database: PostgreSQL 18.x` | Thiếu `flyway-database-postgresql` | Đã khai báo sẵn trong `pom.xml`; nếu bạn xoá mất thì thêm lại (xem §2 #3) |
| `HikariPool-1 - Exception during pool initialization ... Connection refused` | PostgreSQL chưa chạy hoặc sai port/DB | Kiểm tra service PostgreSQL; so `DB_URL` với `psql -h localhost -U minisoc -d minisoc_db` |
| `FATAL: password authentication failed for user "minisoc"` | Sai mật khẩu hoặc `pg_hba.conf` để `peer`/`trust` | Xem ENVIRONMENT-SETUP.md §4.3 |
| `Web server failed to start. Port 8080 was already in use` | Có app khác dùng 8080 | `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` |
| Test `noPlaintextSecretIsCommitted` fail | Bạn vừa viết mật khẩu thật vào `application*.yml` | **Đừng sửa test** — hãy đưa giá trị đó ra biến môi trường. Đây chính là mục đích của test |
| Test `everyConfigurationFileIsValidYaml` fail | Sai cú pháp YAML | Message nêu tên file; kiểm tra dấu `:` và thụt lề (YAML **không** cho tab) |
| Tiếng Việt trong console bị lỗi font | Console Windows mặc định không phải UTF-8 | `chcp 65001` trước khi chạy, hoặc đặt `JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8` |
| `mvn` tải rất lâu ở lần đầu | Maven đang tải dependency vào `~/.m2` | Bình thường. Lần sau nhanh vì đã cache |

---

## 7. NHỮNG THỨ TÔI **CỐ Ý KHÔNG** LÀM Ở PHASE 1 (và vì sao)

| Không làm | Vì sao — và sẽ làm ở đâu |
|---|---|
| Không có entity / migration | Phase 2 mới thiết kế schema. Viết entity trước khi có schema là làm ngược |
| Không có `SecurityConfig` | Phase 3. Hiện tại mọi endpoint mặc định… chưa có endpoint nào cả |
| Không có `logback-spring.xml` (4 loại log theo NFR-OBS-01) | Phase 5, khi đã có event/alert/audit để ghi. Cấu hình appender cho những log chưa tồn tại là cấu hình rỗng |
| Không thêm `thymeleaf-extras-springsecurity6` | Phase 3, khi template cần `sec:authorize`. **Đã xác minh tên artifact trong BOM Boot 4.1** để Phase 3 không phải tra lại |
| Không thêm JaCoCo (coverage) | Phase 12 (NFR-TEST-01 cần ≥ 60%). Bây giờ chưa có đủ code để đo có ý nghĩa |
| Không dùng Lombok | Quyết định của Phase 0: code sinh tự động làm bạn khó thấy điều gì thật sự xảy ra (getter/setter/constructor). Với 16 entity, viết tay vẫn kiểm soát được và học được nhiều hơn |
| Không tạo `mvnw` (Maven Wrapper) | Bạn đã cài Maven 3.9.16; wrapper cần tải thêm file. Có thể thêm ở Phase 13 nếu muốn máy khác chạy được mà không cài Maven |
| Không cấu hình `spring.jpa.open-in-view` khác đi | Đã đặt `false` ngay từ đầu vì đây là quyết định kiến trúc, không phải tinh chỉnh nhỏ |
| Không viết `@ConfigurationProperties` class | Phase 6, khi Detection Engine dùng các ngưỡng trong `app.security.detection` |

---

## 8. TỔNG KẾT — PHASE 1 DẠY ĐƯỢC GÌ

**Về Java / Spring:**

1. **Maven parent POM là cơ chế quản lý version, không phải "copy cho có".** Nhờ nó, `pom.xml` không có một số `<version>` nào cho Spring/Hibernate/Flyway ⇒ không thể lệch version.
2. **Dependency scope là công cụ thiết kế.** Driver PostgreSQL ở `runtime` buộc code phải sạch khỏi `import org.postgresql.*`.
3. **`@SpringBootApplication` = config + auto-config + component scan**, và **vị trí package quyết định phạm vi scan** — một chi tiết nhỏ gây lỗi rất khó tìm nếu làm sai.
4. **Cấu hình là code.** Nó cũng cần test: `ConfigurationFilesTest` bắt được typo YAML, secret bị commit, và cấu hình nguy hiểm (mất `UTC`, bật `ddl-auto`) **ngay ở bước build**.
5. **Java 21 có API mới thật sự dùng được**: `List#getFirst()` (SequencedCollection) xuất hiện trong test.
6. **Không được đoán API của framework.** Sáu khác biệt của Boot 4 ở §2 đều là thứ mà "trí nhớ theo Boot 3" sẽ làm sai. Cách làm đúng: kiểm tra source/tài liệu chính thức của **đúng version** mình đang dùng.

**Về kỹ thuật thực tế:**

* `externalized configuration` + `fail-fast` là cách xử lý secret đúng đắn: profile `prod` **không** có default ⇒ thiếu biến là dừng ngay, không chạy với mật khẩu đoán mò.
* **Hai cơ chế placeholder khác nhau cùng tồn tại trong một file** (`${...}` runtime của Spring, `@...@` build-time của Maven) — hiểu sai chỗ này làm hỏng toàn bộ cấu hình ngay lần build đầu.

---

## 9. CHECKLIST TRƯỚC KHI SANG PHASE 2

- [ ] `mvn -q clean compile` → thành công.
- [ ] `mvn test` → 11 test xanh.
- [ ] `mvn -q clean package` → có `target/mini-soc-dashboard-0.0.1-SNAPSHOT.jar`.
- [ ] (Nếu đã có PostgreSQL) `mvn spring-boot:run` → log có `Started MiniSocApplication`.
- [ ] (Nếu đã có PostgreSQL) `curl localhost:8080/actuator/health` → `{"status":"UP"}`.
- [ ] Bạn giải thích được **bằng lời của mình**: `spring-boot-starter-webmvc` khác gì `spring-boot-starter-web`? Vì sao `flyway-database-postgresql` phải khai báo tay? Vì sao `${DB_PASSWORD:}` an toàn còn `password: 123456` thì không?

### Câu hỏi tự kiểm tra (nếu trả lời được là bạn đã nắm Phase 1)

1. Nếu xoá `@SpringBootApplication` khỏi `MiniSocApplication`, ứng dụng sẽ lỗi thế nào?
2. Nếu chuyển `MiniSocApplication` vào package `com.yak.minisoc.web`, điều gì sẽ xảy ra với các bean trong `com.yak.minisoc.service`?
3. Vì sao driver PostgreSQL để `runtime` lại tốt hơn `compile`?
4. Nếu `application.yml` dùng `${project.version}` thay cho `@project.version@`, Maven sẽ làm gì lúc build? (Gợi ý: Boot 4 đặt `resource.delimiter` là `@` — vì sao?)
5. Vì sao `ConfigurationFilesTest` **không** cần database vẫn kiểm tra được cấu hình database?
6. Điều gì xảy ra khi chạy `SPRING_PROFILES_ACTIVE=prod` mà không set `DB_PASSWORD`? Vì sao đó là hành vi **mong muốn**?

---

## 10. ĐỀ XUẤT TIẾP THEO — PHASE 2 (DATABASE)

Phase 2 sẽ tạo:

1. **8 file Flyway**: `V1__create_users_and_roles.sql` … `V8__add_constraints_and_indexes.sql` (16 bảng: users, roles, user_roles, login_history, audit_logs, categories, products, carts, cart_items, orders, order_items, security_events, security_alerts, security_incidents, incident_alerts, incident_notes).
2. **16 JPA entity** + `BaseEntity` (kế thừa: `id`, `createdAt`, `updatedAt`, `@Version`).
3. **Repository interface** cho từng entity.
4. **Test**: `@DataJpaTest` chạy trên database test riêng (`minisoc_test`) — **quan trọng**: với PostgreSQL thật, không dùng H2, vì schema có `JSONB` và `TIMESTAMPTZ` mà H2 không mô phỏng đúng.
5. Kiểm chứng: `\dt` thấy 16 bảng + `flyway_schema_history` có 8 dòng.

**Điều bạn cần chuẩn bị cho Phase 2:** PostgreSQL 18 đang chạy + database `minisoc_db` (role `minisoc`) + thêm database `minisoc_test` cùng owner:

```sql
CREATE DATABASE minisoc_test OWNER minisoc ENCODING 'UTF8';
```

---

## ✅ KẾT THÚC PHASE 1

Hãy chạy các lệnh ở §5 (ít nhất Bước 1–3, không cần database) và **dán kết quả cho tôi**. Tôi sẽ:
* sửa mọi lỗi ngay trong Phase 1 (không nhảy sang Phase 2 khi còn lỗi — SDD §20), và
* chỉ bắt đầu Phase 2 khi bạn xác nhận `BUILD SUCCESS`.
