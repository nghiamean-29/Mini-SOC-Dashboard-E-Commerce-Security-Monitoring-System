# PHASE 3 — AUTHENTICATION & AUTHORIZATION (Spring Security 7 + RBAC 3 role)

| | |
|---|---|
| **Document ID** | `PHASE-03-AUTHENTICATION` |
| **Ngày** | 2026-10-11 |
| **Trạng thái** | ✅ Code xong (15 class chính + 7 template + 8 lớp test) — ⏳ **chờ bạn chạy `mvn test` và đăng nhập thử** (sandbox không có JDK nên chưa biên dịch lần nào) |
| **Tiền đề** | Phase 2: schema 17 bảng + 14 entity + 14 repository |
| **Phase trước** | [PHASE-02-DATABASE.md](PHASE-02-DATABASE.md) |
| **Phase sau** | Phase 4 — E-Commerce Core (catalog, giỏ hàng, đơn hàng) |
| **Requirement liên quan** | FR-AUTH-01…06 (MUST), một phần FR-AUTH-07…09 · UC-01, UC-02, UC-03, UC-05 · A1–A4 |

---

## 0. GATE CỦA PHASE 3 (điều kiện để sang Phase 4)

> **1)** `mvn test` xanh — trong đó **8 lớp test mới** của Phase 3, quan trọng nhất là
> `SecurityConfigTest` (24 test, ma trận phân quyền theo role) và `ErrorPageRenderingTest`
> (6 test, chạy trên **Tomcat thật** + HTTP thật).
> **2)** Kiểm tra bằng tay trong trình duyệt: đăng ký → đăng nhập → **403 đúng vai trò**
> (CUSTOMER mở `/admin` ⇒ trang 403 của mình, không phải trang trắng).
> **3)** Đăng xuất được, và sau khi đăng xuất thì các URL được bảo vệ lại yêu cầu đăng nhập.

Điểm khác so với Phase 2: Phase 2 chứng minh *dữ liệu*; Phase 3 chứng minh **hành vi của ứng
dụng khi có người dùng thật** — nên phần kiểm chứng bằng tay (§5.4) quan trọng ngang với test.

---

## 1. NHỮNG GÌ ĐÃ ĐƯỢC TẠO

```
src/main/java/com/yak/minisoc/
├── security/
│   ├── AppRoles.java                 3 role (CUSTOMER/ADMIN/SOC_ANALYST) + authority() → "ROLE_…"
│   ├── AuthenticatedUser.java        UserDetails: mang theo userId + UserStatus từ DB
│   └── DatabaseUserDetailsService.java  đọc users + roles từ DB khi đăng nhập
├── config/
│   ├── SecurityConfig.java           ★ toàn bộ chính sách: URL → role, login, logout, session, header
│   ├── BootstrapAdminProperties.java  bind app.bootstrap.admin.* (từ biến môi trường)
│   └── AdminBootstrapConfig.java     tạo ADMIN đầu tiên (idempotent, KHÔNG log password)
├── dto/RegisterRequest.java          dữ liệu form đăng ký + Bean Validation (JavaBean, không record)
├── validation/
│   ├── PasswordConfirmed.java        "capability" interface (password + confirmPassword)
│   ├── PasswordMatches.java          @PasswordMatches — ràng buộc Ở MỨC CLASS
│   └── PasswordMatchesValidator.java so sánh 2 trường mật khẩu
├── exception/
│   ├── BusinessException.java        base cho lỗi "hiển thị được cho người dùng"
│   └── DuplicateAccountException.java username/email đã tồn tại (biết rõ trường nào)
├── service/AuthService.java          register() / createAccount() / accountExists()
└── controller/
    ├── AuthController.java           GET /login, GET+POST /register (KHÔNG có POST /login)
    └── HomeController.java           GET / + các cờ hiển thị menu theo role

src/main/resources/
├── templates/
│   ├── fragments/layout.html         head(title) · nav(5 tham số) · footer
│   ├── index.html                    trang chủ (hero, CTA theo role, pipeline, trạng thái phase)
│   ├── login.html                    thông báo ?error/?logout/?expired/?registered
│   ├── register.html                 lỗi từng trường + khối lỗi chung
│   └── error/{403,404,error}.html    trang lỗi do container render
└── static/{css/app.css, favicon.svg} dark theme, KHÔNG framework/CSS ngoài, KHÔNG inline style

src/test/java/com/yak/minisoc/
├── config/SecurityConfigTest.java            24 test — ma trận phân quyền + login/logout (MockMvc)
├── config/ErrorPageRenderingTest.java         6 test — Tomcat thật: header, 302, 403/404, cookie
├── controller/AuthControllerTest.java        12 test — @WebMvcTest slice của /login, /register
├── service/AuthServiceTest.java              12 test — @DataJpaTest: đăng ký, trùng, transaction
├── service/AuthServiceConstraintHandlingTest.java  5 test — mô phỏng race của UNIQUE constraint
├── security/DatabaseUserDetailsServiceTest.java    6 test — đọc user + role, status
├── security/AuthenticatedUserTest.java       10 test — roles → authorities, cờ status
└── validation/RegisterRequestValidationTest.java   10 test — rule của username/password
```

Sửa đổi so với Phase 2: `pom.xml` (thêm 2 starter test), `application.yml` (thêm khối
`app.bootstrap.admin.*`).

### 1.1. Ma trận phân quyền — những gì ĐÃ cài trong Phase 3

| URL | Guest | CUSTOMER | SOC_ANALYST | ADMIN |
|---|---|---|---|---|
| `/`, `/login`, `/register`, `/error` | ✅ | ✅ | ✅ | ✅ |
| `/css/**`, `/js/**`, `/images/**`, `/vendor/**`, `/favicon.svg` | ✅ | ✅ | ✅ | ✅ |
| `/actuator/health`, `/actuator/info` | ❌ (302 → login) | ❌ | ❌ | ✅ |
| `/admin/**` | ❌ | ❌ **403** | ❌ **403** | ✅ |
| `/soc/**` | ❌ | ❌ **403** | ✅ | ✅ |
| `/cart/**`, `/orders/**` | ❌ | ✅ | ❌ **403** | ✅ |
| mọi URL khác (`anyRequest()`) | ❌ | ✅ (đã đăng nhập) | ✅ | ✅ |

Giống ma trận §11.2 của [PHASE-0-PLANNING.md](PHASE-0-PLANNING.md) ở các dòng **đã tồn tại**.
Các dòng của phase sau (`/soc/simulator/**`, `/api/stats/**`, `/api/alerts/**`, `/ws`) hiện được
che bởi `/soc/**` hoặc `anyRequest().authenticated()`, và sẽ được tách riêng khi phase đó làm
(Phase 9/10/11) — ghi ở §8 để không quên.

> ⚠️ **Thay đổi hành vi so với Phase 1:** `/actuator/health` **trước đây mở công khai**, nay chỉ
> ADMIN xem được (anonymous bị chuyển hướng về `/login`). Đây là chủ ý: tình trạng database
> không phải thông tin công khai. Khi kiểm tra bằng trình duyệt mà không đăng nhập, bạn sẽ thấy
> trang đăng nhập thay vì JSON `{"status":"UP"}`.

### 1.2. Hai tầng phân quyền

| Tầng | Ở đâu | Vai trò |
|---|---|---|
| URL pattern | `SecurityConfig.securityFilterChain()` | Tuyến đầu, nhìn một màn hình là thấy hết |
| Method (`@PreAuthorize`) | các controller của Phase 4+ | Defense in depth: URL có bị nới lỏng thì hành động nhạy cảm vẫn được bảo vệ |

`@EnableMethodSecurity` đã bật sẵn trong `SecurityConfig` — Phase 4 sẽ dùng ngay
(`@PreAuthorize("hasRole('ADMIN')")` cho CRUD sản phẩm).

---

## 2. NHỮNG QUYẾT ĐỊNH THIẾT KẾ (và lý do)

| # | Quyết định | Lý do (tóm tắt) |
|---|---|---|
| D1 | **Form login + session** (không JWT) | Ứng dụng render HTML ở server, không có client mobile. Session + cookie `HttpOnly` không cần thêm code cho việc thu hồi (logout là thu hồi ngay); JWT sẽ thêm phức tạp mà không giải quyết vấn đề nào ở đây. JWT vẫn được nhắc trong báo cáo như *hướng mở rộng* nếu tách REST API cho client khác |
| D2 | **BCrypt strength 12** | Có salt ngẫu nhiên + cố tình chậm (~0,25–0,5 s/lần hash): đăng nhập không ai thấy khác, nhưng brute force ngoài đời trở nên đắt. Strength 12 là mức khuyến nghị cho 2026. Test `SecurityConfigTest#passwordEncoderIsBcryptWithStrengthTwelve` khẳng định lại đúng cost factor trong hash (`$2a$12$`) |
| D3 | **Authority suy ra từ `roles.code`** | DB giữ mã trần (`ADMIN`), tầng Spring Security thêm tiền tố (`ROLE_ADMIN`) tại **một chỗ duy nhất** (`AppRoles.authority()`). Không rải chuỗi `"ROLE_"` khắp code |
| D4 | **FR-AUTH-06 chỉ làm phần 403** | Requirement ghi "403 **+ event `ACCESS_DENIED`**" và bảng §8 của planning ghi phase **3, 5**: event thuộc Phase 5 (cần bảng `security_events`, listener). Phase 3 làm 403; trang 403 đã *nói trước* điều này để người dùng không thấy lệch tài liệu |
| D5 | **FR-AUTH-10 (soft-lockout) = WON'T, không implement** | Đây là quyết định đã chốt ở Phase 0 (§FR-AUTH-10): nếu tự khoá tài khoản sau N lần sai thì brute force bị chặn **trước khi** detection engine kịp phát hiện → demo không còn dữ liệu. Trong dự án này, *phát hiện* là mục tiêu; chặn thật (rate limit/lockout) ghi nhận là future work |
| D6 | **CSRF luôn bật**, logout là `POST` | `th:action="@{...}"` khiến Thymeleaf tự chèn hidden field `_csrf` → không có bước "nhớ thêm token" nào để quên. Logout bằng `GET` có thể bị kích hoạt bằng `<img src="/logout">` từ website khác |
| D7 | **1 session / tài khoản, login sau thắng** | `maximumSessions(1)` + `maxSessionsPreventsLogin(false)`: session cũ bị đẩy ra và người dùng được đưa tới `/login?expired`. Thực tế cho demo (mở 2 browser), và session bị đánh cắp không thể dùng song song âm thầm |
| D8 | **Session fixation: `changeSessionId`** | Ghi rõ ra dù đây là mặc định của Spring Security — để người đọc thấy đó là bảo vệ *có chủ ý*, và để một refactor sau này không vô tình tắt nó |
| D9 | **Không bật remember-me** | Giảm bề mặt tấn công; đồ án chạy local không cần "nhớ đăng nhập" 30 ngày |
| D10 | **`@PasswordMatches` ở mức CLASS** | Ràng buộc so sánh *hai trường* nên không thuộc về một trường nào. Hệ quả: khi sai, lỗi hiện ở **`#fields.globalErrors()`** — nên `register.html` có một khối lỗi chung, không phải lỗi của ô "confirm". Đây là chi tiết dễ làm sai và dễ mất điểm trình bày, nên có hẳn chú thích trong template |
| D11 | **Cờ menu tính trong `HomeController`** (`showShopLinks`, `showSocLinks`, `showAdminLinks`) | Trong Thymeleaf, `${isCustomer or isAdmin}` nổ lỗi khi biến là `null`, và dùng `<th:unless>` thay cho `${not x}` cho an toàn. Cách này còn giúp **test được bằng Java** (`SecurityConfigTest` kiểm tra link SOC có/không theo role) và không phải thêm dependency `thymeleaf-extras-springsecurity` (bớt một library phải tương thích với Boot 4) |
| D12 | **`RegisterRequest` là JavaBean, không phải `record`** | `th:field` và `BindingResult.rejectValue` cần getter/setter và constructor rỗng. `record` là lựa chọn sai cho form binding |
| D13 | **Form đăng ký không có trường `role`** | Người dùng không thể tự gán role cho mình (leo thang đặc quyền). Đăng ký luôn chỉ cấp CUSTOMER; ADMIN chỉ sinh ra từ `AuthService.createAccount()` hoặc từ biến môi trường bootstrap; SOC_ANALYST gán bằng SQL (§5.3) hoặc trang quản trị ở Phase 8 |
| D14 | **Chỉ dịch 2 UNIQUE constraint thật** (`uk_users_username`, `uk_users_email`) thành lỗi thân thiện | `AuthService` bắt `DataIntegrityViolationException` khi `saveAndFlush`, chỉ 2 tên constraint này được dịch thành `DuplicateAccountException`; lỗi khác **ném tiếp** (500). Dịch bừa mọi lỗi ràng buộc sẽ giấu bug thật |
| D15 | **Kiểm thử ở 2 mức: MockMvc + Tomcat thật** | MockMvc **không có error dispatch** (không phải container) nên không bao giờ render được `error/403.html` — nó chỉ chứng minh *status*. Trang lỗi phải kiểm bằng HTTP thật ⇒ `ErrorPageRenderingTest` (`RANDOM_PORT` + JDK `HttpClient`, không thêm thư viện nào) |
| D16 | **CSP không có `'unsafe-inline'`** | `default-src 'self'` + `script-src/style-src 'self'` là tuyến thứ hai chống XSS (T8): có chèn được markup thì script cũng không có gì để chạy. Vì thế `app.css` là file riêng và **không có inline style** trong template. Thêm `frame-ancestors 'none'` (clickjacking), `form-action 'self'` (chống form gửi credential sang nơi khác) |
| D17 | **Bootstrap ADMIN qua biến môi trường, idempotent** | Cần một ADMIN đầu tiên để gán role cho người khác, nhưng không được để mật khẩu trong repo. `AdminBootstrapConfig` chỉ tạo khi `accountExists()` = false, **không log mật khẩu**, và tắt ở profile `test` (`@Profile("!test")`) để test không phụ thuộc cấu hình máy |

---

## 3. BẰNG CHỨNG KIỂM CHỨNG

### 3.1. Những gì đã kiểm chứng được (không cần JDK)

Sandbox phát triển **không có JDK/Maven**, nên toàn bộ phần Java dưới đây được kiểm tra *tĩnh*:
đối chiếu từng import với **mã nguồn thật** của Spring Boot 4.1.1 / Spring Security 7.1.1 /
Spring Framework 7.0.5 (đọc trực tiếp trên GitHub bằng `gh api`).

| Hạng mục | Cách kiểm | Kết quả |
|---|---|---|
| Gói của mọi annotation Boot/Security dùng trong code | So từng đường dẫn file nguồn | ✅ Trừ 1 lỗi đã sửa: `@AutoConfigureMockMvc` **đã đổi gói** ở Boot 4 → `org.springframework.boot.webmvc.test.autoconfigure` |
| `WebMvcSecurityConfiguration` có đăng ký `CsrfRequestDataValueProcessor`? | Đọc `config/.../web/servlet/configuration/WebMvcSecurityConfiguration.java` | ✅ Có (`@Bean RequestDataValueProcessor`) ⇒ `th:action` **thật sự** sinh hidden field `_csrf` |
| Thành phần của các starter test | Đọc `starter/*/build.gradle` của Boot v4.1.1 | ✅ `starter-test` có JUnit 5/AssertJ/Hamcrest/Mockito; `starter-webmvc-test` có `spring-boot-webmvc-test` (+ module `spring-boot-resttestclient`); `starter-data-jpa-test` có `spring-boot-jdbc-test` |
| Cân bằng ngoặc, số `@Test`, tên method trùng | Script kiểm tra tĩnh | ✅ 24 test / `SecurityConfigTest`, 6 test / `ErrorPageRenderingTest`, không có method trùng tên |
| SQL: 9 migration + 64 ràng buộc | `tools/sql-validation` (PGlite = PostgreSQL 18.3) | ✅ 64/64 PASS (Phase 2, không đổi trong Phase 3) |

### 3.2. Những gì **chỉ bạn** chạy được (`mvn`)

```bash
cd Mini-SOC-Dashboard-E-Commerce-Security-Monitoring-System
git pull --ff-only
# Windows: $env:DB_PASSWORD="matkhau-cua-ban"; $env:TEST_DB_PASSWORD="matkhau-cua-ban"
export DB_PASSWORD='matkhau-cua-ban'
mvn test
```

Kết quả mong đợi: **18 lớp test, 158 test method, `Failures: 0, Errors: 0`**. Riêng Phase 3
thêm 8 lớp / **85 test method**:

| Lớp | Kiểm cái gì | Cần gì để chạy |
|---|---|---|
| `SecurityConfigTest` (24) | Ma trận 403 theo role, trang công khai, Thymeleaf render thật `index`/`login`/`register`, đăng ký→đăng nhập, tài khoản LOCKED/DISABLED, logout + CSRF, BCrypt strength 12 | PostgreSQL `minisoc_test` |
| `ErrorPageRenderingTest` (6) | Header bảo mật thật, 302 khi anonymous, **404 template**, **403 template** (đăng nhập thật bằng cookie + CSRF), logout | PostgreSQL `minisoc_test` |
| `AuthControllerTest` (12) | Validate form, PRG, lỗi trùng tài khoản → field error, POST thiếu CSRF → 403 | không cần DB (mock `AuthService`) |
| `AuthServiceTest` (12) | Đăng ký, chuẩn hoá username/email, trùng username/email, rollback, `createAccount()` | PostgreSQL `minisoc_test` |
| `AuthServiceConstraintHandlingTest` (5) | Race condition của UNIQUE constraint (mô phỏng bằng mock) | không cần DB |
| `DatabaseUserDetailsServiceTest` (6) | Load user + roles trong 1 truy vấn, username phân biệt hoa/thường | PostgreSQL `minisoc_test` |
| `AuthenticatedUserTest` (10) | `roles.code` → `ROLE_*`, cờ `isAccountNonLocked/…`, `equals/hashCode` | không cần DB |
| `RegisterRequestValidationTest` (10) | Rule username/password, lỗi "2 mật khẩu không khớp" | không cần DB |

### 3.3. Version matrix (để ghi vào báo cáo)

| Thành phần | Version | Ghi chú |
|---|---|---|
| Spring Boot | **4.1.1** | Bản 4.1 là dòng đang được hỗ trợ (3.5 hết hỗ trợ 06/2026) |
| Spring Security | **7.1.1** | Kéo theo bởi Boot 4.1.1 |
| Spring Framework | **7.0.9** | |
| Hibernate | 7.4.5.Final | |
| Flyway | 12.4.0 | |
| Java | **21 (LTS)** | |
| PostgreSQL | **18.x** (test trên 18.3) | |

### 3.4. Bốn "bẫy" của Spring Boot 4 gặp trong phase này

| Bẫy | Triệu chứng | Cách xử lý |
|---|---|---|
| Annotation test **đổi gói** | `cannot find symbol: class AutoConfigureMockMvc` | Boot 4 tách module: `@WebMvcTest`/`@AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure`; `@DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure`; `@AutoConfigureTestDatabase` → `org.springframework.boot.jdbc.test.autoconfigure` |
| `@DataJpaTest` **không chạy Flyway** | Test slice báo thiếu bảng | `@ImportAutoConfiguration(FlywayAutoConfiguration.class)` (đã làm ở Phase 2) |
| `TestRestTemplate` **đổi module** | import cũ không tồn tại | Boot 4 đưa nó sang module `spring-boot-resttestclient` (`org.springframework.boot.resttestclient`). Ở đây **không dùng** — `ErrorPageRenderingTest` dùng `java.net.http.HttpClient` của JDK, không thêm dependency |
| MockMvc **không render trang lỗi** | Test mong đợi nội dung `403.html` nhưng body rỗng | Tách thành 2 lớp test (D15): MockMvc kiểm status, Tomcat thật kiểm HTML |

---

## 4. ĐỌC CODE Ở ĐÂU TRƯỚC?

1. `config/SecurityConfig.java` — **file quan trọng nhất**: toàn bộ chính sách nằm trong một hàm,
   chia 5 khối có chú thích (authorization → login → logout → session → headers).
2. `security/DatabaseUserDetailsService.java` + `security/AuthenticatedUser.java` — cầu nối
   giữa bảng `users`/`roles` và Spring Security.
3. `service/AuthService.java` — nghiệp vụ đăng ký; để ý `saveTranslatingTheUniqueConstraint()`.
4. `controller/AuthController.java` + `templates/register.html` — luồng form, chỗ dễ sai nhất là
   lỗi chung của `@PasswordMatches`.
5. `config/SecurityConfigTest.java` → `config/ErrorPageRenderingTest.java` — hai lớp test đọc
   như một bản mô tả hành vi mong đợi.

---

## 5. BẠN CẦN LÀM GÌ ĐỂ XÁC NHẬN PHASE 3

### 5.1. Lấy code + chạy test

```bash
git pull --ff-only
mvn test
```

Nếu chưa có database test (Phase 2 đã tạo rồi thì bỏ qua):

```sql
-- psql -U postgres
CREATE DATABASE minisoc_test OWNER minisoc ENCODING 'UTF8';
```

### 5.2. Tạo tài khoản ADMIN đầu tiên (bootstrap)

Ứng dụng **không** có sẵn tài khoản ADMIN nào (không hard-code mật khẩu). ADMIN đầu tiên được
tạo từ biến môi trường — **chỉ tạo khi tài khoản chưa tồn tại**:

```bash
# Linux / macOS
export APP_BOOTSTRAP_ADMIN_USERNAME=admin01
export APP_BOOTSTRAP_ADMIN_PASSWORD='Admin@12345'
export APP_BOOTSTRAP_ADMIN_EMAIL=admin01@example.com
export APP_BOOTSTRAP_ADMIN_FULL_NAME='Quan tri vien'
export DB_PASSWORD='matkhau-cua-ban'
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run
```

```powershell
# Windows PowerShell
$env:APP_BOOTSTRAP_ADMIN_USERNAME="admin01"
$env:APP_BOOTSTRAP_ADMIN_PASSWORD="Admin@12345"
$env:APP_BOOTSTRAP_ADMIN_EMAIL="admin01@example.com"
$env:DB_PASSWORD="matkhau-cua-ban"
$env:SPRING_PROFILES_ACTIVE="dev"
mvn spring-boot:run
```

Trong log sẽ thấy đúng khối này (mật khẩu **không bao giờ** được in ra):

```text
================================================================
 Bootstrap administrator created: username = admin01
 Password: the value of APP_BOOTSTRAP_ADMIN_PASSWORD (not logged).
 => Log in at http://localhost:8080/login and enable the admin area.
================================================================
```

Chạy lại lần sau chỉ thấy một dòng: `Bootstrap administrator 'admin01' already exists - nothing
to create.` — đó là bằng chứng tính idempotent. Nếu không đặt biến môi trường, log ghi
`No bootstrap administrator configured (...)`, và ứng dụng vẫn chạy bình thường. Đổi mật khẩu về sau:
xem §8 (Phase 5 mới có trang đổi mật khẩu; tạm thời có thể chạy lại app ở profile `dev` sau khi
xoá tài khoản, hoặc cập nhật `password_hash` bằng một hash BCrypt mới).

### 5.3. Gán role `SOC_ANALYST` cho một tài khoản (SQL)

Tài khoản tạo qua form `/register` chỉ có CUSTOMER. Để thử vai trò SOC:

```sql
-- Bước 1: đăng ký một tài khoản bình thường trên web (ví dụ analyst01), rồi:
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'analyst01'
  AND r.code = 'SOC_ANALYST'
ON CONFLICT DO NOTHING;      -- PK của user_roles là (user_id, role_id)

-- Bước 2: kiểm tra
SELECT u.username, r.code
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r       ON r.id = ur.role_id
WHERE u.username = 'analyst01';
```

> ⚠️ **Phải đăng xuất rồi đăng nhập lại.** Role được đọc một lần khi đăng nhập và lưu vào
> session; thêm dòng trong DB không tự động đổi quyền của session đang mở (đúng như thiết kế:
> quyền không thay đổi ở giữa phiên làm việc).

Cần bỏ role thì xoá đúng dòng đó:

```sql
DELETE FROM user_roles
WHERE user_id = (SELECT id FROM users WHERE username = 'analyst01')
  AND role_id = (SELECT id FROM roles WHERE code = 'SOC_ANALYST');
```

### 5.4. Kịch bản kiểm tra bằng tay (đây là "gate" thật của phase)

Chạy app (mục 5.2) rồi mở <http://localhost:8080>.

| # | Làm gì | Kết quả đúng | Nếu khác thì sao |
|---|---|---|---|
| 1 | Mở `/` khi chưa đăng nhập | Trang chủ hiện, menu có **Đăng nhập / Đăng ký**, không có link "SOC Dashboard"/"Quản trị" | Xem §6 |
| 2 | `/register` → tạo tài khoản (mật khẩu ≥ 8 ký tự, có chữ **và** số) | Chuyển về `/login?registered` và báo "Đăng ký thành công" | Nếu thấy lỗi đỏ dưới ô mật khẩu → rule mật khẩu; lỗi ở **khối trên cùng** → 2 mật khẩu không khớp (`@PasswordMatches`) |
| 3 | Đăng nhập bằng tài khoản vừa tạo | Về trang chủ, góc phải hiện **tên tài khoản** + nút **Đăng xuất** | Sai mật khẩu → quay lại `/login?error` với thông báo trung lập |
| 4 | Mở `/admin/products` khi đang là CUSTOMER | **Trang 403** của dự án ("Bạn không có quyền truy cập trang này" + nhắc `ACCESS_DENIED`) | Nếu ra **trang trắng** hoặc lỗi whitelabel → `templates/error/403.html` không được render; xem §6 |
| 5 | Mở `/soc` khi đang là CUSTOMER | **403** (giống bước 4) | 404 nghĩa là rule `/soc/**` không khớp |
| 6 | Mở `/cart`, `/orders` khi đang là CUSTOMER | **404** — *đúng*, vì phân quyền đã cho qua, chỉ còn thiếu giao diện (Phase 4) | **403** ở đây là bug (rule quá chặt) |
| 7 | Bấm **Đăng xuất** | Về `/login?logout`; sau đó mở `/cart` → bị đưa về `/login` | Nếu `/logout` ra 403: form logout không phải POST hoặc thiếu token |
| 8 | F12 → Network → mở `/` và xem Response Headers | Có `Content-Security-Policy` (không có `unsafe-inline`), `X-Frame-Options: DENY`, `Referrer-Policy: same-origin`; cookie `JSESSIONID` có `HttpOnly` | — |
| 9 | Gán `SOC_ANALYST` (§5.3) → **đăng xuất, đăng nhập lại** | Menu hiện link **SOC Dashboard**; `/soc` trả **404** (đã qua phân quyền, chưa có trang); `/admin/**` vẫn **403** | `/soc` ra 403 ⇒ session cũ chưa được cấp role mới |
| 10 | Đăng nhập ADMIN (bootstrap) | `/admin/**` → **404** (chưa có trang, Phase 4/8); `/actuator/health` → JSON `{"status":"UP"}` | `/actuator/health` trả về trang login ⇒ bạn chưa đăng nhập ADMIN |
| 11 | Mở 2 browser khác nhau, đăng nhập **cùng một tài khoản** | Browser thứ hai thắng; browser thứ nhất bị đẩy ra ở request kế tiếp (`/login?expired`) | Đây là hành vi có chủ ý (D7) |

### 5.5. Checklist nhanh

```text
[ ] mvn test                       → 158 test, 0 failure, 0 error
[ ] mvn spring-boot:run (dev)      → "Started MiniSocApplication"
[ ] /register + /login             → vào được trang chủ, thấy tên mình
[ ] CUSTOMER mở /admin và /soc     → 403 (trang 403 của dự án)
[ ] CUSTOMER mở /cart, /orders     → 404
[ ] SOC_ANALYST (sau khi gán SQL)   → /soc 404, /admin 403
[ ] ADMIN                          → /admin 404, /actuator/health = UP
[ ] logout                         → /login?logout, và /cart trở lại yêu cầu đăng nhập
```

---

## 6. XỬ LÝ SỰ CỐ THƯỜNG GẶP

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| Mọi form đều báo **403 / "Invalid CSRF token"** | Form không dùng `th:action` (hoặc tự viết `<form action="/login">`) | Giữ `th:action="@{...}"`; Thymeleaf + Spring Security sẽ tự chèn `_csrf`. Kiểm tra bằng F12 xem form có `<input type="hidden" name="_csrf">` |
| Đăng nhập đúng nhưng mở `/soc` vẫn **403** | Role được gán sau khi session đã mở | Đăng xuất → đăng nhập lại (role đọc tại thời điểm đăng nhập) |
| `/admin` ra **trang trắng** hoặc lỗi whitelabel thay vì trang 403 của dự án | `templates/error/403.html` không render được | Kiểm tra log có lỗi Thymeleaf; xác nhận `SecurityConfig` vẫn còn dòng `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` và `/error` trong danh sách public |
| Anonymous mở `/actuator/health` thấy trang login | **Đúng thiết kế** (Phase 3 đổi từ công khai sang ADMIN) | Đăng nhập ADMIN rồi thử lại |
| Không có tài khoản ADMIN nào, log không nhắc bootstrap | Biến `APP_BOOTSTRAP_ADMIN_USERNAME/PASSWORD` chưa được set trong **đúng terminal** đang chạy `mvn`, hoặc profile `test` | Set lại biến môi trường rồi chạy lại; mật khẩu trống ⇒ bỏ qua (không tạo tài khoản) |
| `mvn test` lỗi kết nối DB | Chưa tạo `minisoc_test`, hoặc sai `TEST_DB_PASSWORD` | Xem [PHASE-02-DATABASE.md §5.1](PHASE-02-DATABASE.md) |
| `Password encoder does not look like BCrypt` / sai mật khẩu | Mật khẩu trong DB là plaintext (do gán tay bằng SQL) | Đăng ký qua form, hoặc cập nhật `password_hash` bằng **hash BCrypt** (`{bcrypt}` không cần tiền tố trong dự án này) |
| Cổng 8080 bận | Ứng dụng khác đang chạy | `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` |

---

## 7. FAQ (câu hỏi dễ bị hỏi khi bảo vệ)

**1. Vì sao không dùng JWT?**
Vì đây là ứng dụng render HTML ở server: mỗi request đã có cookie session, logout là thu hồi
ngay, không cần cơ chế refresh/blacklist. Dùng JWT ở đây sẽ thêm phức tạp mà không có lợi ích nào,
và còn dễ làm sai (lưu token ở `localStorage` ⇒ tăng rủi ro XSS). JWT nằm trong phần *hướng mở rộng*
nếu tách REST API cho client mobile.

**2. Vì sao hash trong DB bắt đầu bằng `$2a$12$`?**
`$2a` = thuật toán BCrypt, `12` = cost factor (2^12 vòng lặp). Đây là bằng chứng *cấu hình*
thực sự tới được thuật toán — nếu ai đó tạo `new BCryptPasswordEncoder()` không tham số, chuỗi sẽ
là `$2a$10$`. Test `SecurityConfigTest` khẳng định điều này.

**3. Vì sao đăng nhập sai mật khẩu và sai tài khoản cho **cùng một** thông báo?**
Để kẻ tấn công không dùng form đăng nhập như một công cụ *dò tên tài khoản* (user enumeration).
Thông báo cụ thể cho từng trường hợp là một lỗi bảo mật kinh điển.

**4. Vì sao bị từ chối thì trả **403**, nhưng chưa đăng nhập lại **302 về /login**?**
Hai tình huống khác nhau: *chưa xác thực* ⇒ cần đăng nhập (chuyển hướng, và Spring Security nhớ
URL đã yêu cầu để quay lại sau khi đăng nhập); *đã xác thực nhưng thiếu quyền* ⇒ 403 (đăng nhập
lại cũng không giúp gì). Đây là quy ước HTTP chuẩn.

**5. Vì sao có tận 2 lớp test cho bảo mật?**
Vì chúng chứng minh hai thứ khác nhau: `SecurityConfigTest` (MockMvc) chứng minh **quy tắc**
(status theo role), `ErrorPageRenderingTest` (Tomcat thật) chứng minh **cái người dùng nhìn thấy**
(HTML trang 403/404, header, cookie). MockMvc không thể render trang lỗi vì error dispatch thuộc
về servlet container (D15).

**6. `AuthServiceConstraintHandlingTest` mô phỏng race condition — không phải test thật à?**
Đúng, nó **mô phỏng**: mock `saveAndFlush` để ném `DataIntegrityViolationException` với tên
constraint thật. Một test đua 2 transaction thật sẽ chập chờn (flaky) và không ổn định trên máy
sinh viên. Phía DB đã được phủ bởi `UserRepositoryTest` (Phase 2) + harness SQL.

**7. Phase 3 đã "an toàn" chưa? Còn thiếu gì?**
Đã có: xác thực, phân quyền 3 role, chống CSRF, session fixation, BCrypt, CSP, không lộ thông tin
qua thông báo lỗi. Còn thiếu (đúng lộ trình): ghi `login_history`/event `ACCESS_DENIED` (Phase 5),
đổi mật khẩu (Phase 5), quản lý user (Phase 8), HTTPS/HSTS thật (ngoài phạm vi demo local — ghi
rõ trong báo cáo).

---

## 8. TIẾP THEO (PHASE 4 — E-COMMERCE CORE)

Phase 4 sẽ lấp các trang hiện đang trả **404** và dùng lại đúng những gì Phase 3 đã dựng:

| Phase 4 làm | Dùng lại từ Phase 3 |
|---|---|
| Trang catalog + chi tiết sản phẩm (`/products/**` — public) | `fragments/layout.html`, `app.css` |
| Giỏ hàng (`/cart/**`), đặt hàng (`/orders/**`) | Rule phân quyền CUSTOMER+ADMIN đã có |
| CRUD sản phẩm/danh mục cho ADMIN (`/admin/**`) | `@PreAuthorize` (đã bật) + rule `/admin/**` |
| POST form (thêm vào giỏ, đặt hàng) | CSRF + `th:action` đã hoạt động, không phải làm gì thêm |

Việc cần nhớ cho các phase sau (đã ghi ở §1.1): tách rule riêng cho `/soc/simulator/**`,
`/api/**`, `/ws` khi các phase đó bắt đầu.

---

## 9. PHỤ LỤC — DÙNG CHO BÁO CÁO

### 9.1. Requirement ↔ trạng thái sau Phase 3

| ID | Nội dung | Phase theo kế hoạch | Trạng thái sau Phase 3 |
|---|---|---|---|
| FR-AUTH-01 | Đăng ký + Bean Validation | 3 | ✅ Xong (`AuthController` + `RegisterRequest` + 10 test validate) |
| FR-AUTH-02 | Đăng nhập bằng form | 3 | ✅ Xong (form login của Spring Security; **không** tự viết POST handler) |
| FR-AUTH-03 | Đăng xuất, huỷ session | 3 | ✅ Xong (POST-only + `invalidateHttpSession` + xoá cookie) |
| FR-AUTH-04 | BCrypt, không plaintext | 3 | ✅ Xong (strength 12, có test khẳng định cost factor) |
| FR-AUTH-05 | Phân quyền 3 role | 3 | ✅ Xong (ma trận §1.1, 24 test) |
| FR-AUTH-06 | 403 + event `ACCESS_DENIED` | 3, 5 | 🟡 **403 xong ở Phase 3**; event thuộc Phase 5 (theo bảng §8 của planning) |
| FR-AUTH-07 | Đổi mật khẩu | 5 | ⬜ Chưa (Phase 5) |
| FR-AUTH-08 | ADMIN tạo/khoá tài khoản, gán role | 8 | ⬜ Chưa (Phase 8) — Phase 3 đã có `AuthService.createAccount()` làm nền |
| FR-AUTH-09 | `login_history` mọi lần đăng nhập | 5 | ⬜ Chưa (Phase 5) |
| FR-AUTH-10 | Soft-lockout | — | ❌ **WON'T** (quyết định ở Phase 0: để brute force bị *phát hiện*, không bị chặn — xem D5) |

### 9.2. Use case ↔ trạng thái

| UC | Tên | Phase 3 |
|---|---|---|
| UC-01 | Đăng ký tài khoản | ✅ |
| UC-02 | Đăng nhập | ✅ (phần sinh event `LOGIN_FAILED`/`LOGIN_SUCCESS` ở Phase 5) |
| UC-03 | Đăng xuất | ✅ |
| UC-05 | Truy cập bị từ chối (403) | 🟡 trang 403 ✅, event + rule phát hiện ở Phase 5/6 |

### 9.3. Threat model: những gì Phase 3 thực sự giảm thiểu

| # | Mối đe dọa (STRIDE) | Cơ chế đã cài trong Phase 3 | Kiểm chứng bởi |
|---|---|---|---|
| T1 | Spoofing — credential stuffing | BCrypt 12, thông báo lỗi trung lập, không tiết lộ username tồn tại | `SecurityConfigTest` (wrong password / unknown user cùng kết quả) |
| T6 | Elevation of privilege — CUSTOMER gọi `/admin/**` | `SecurityFilterChain` theo URL + `@PreAuthorize` (đã bật) | `SecurityConfigTest` (403 theo role), `ErrorPageRenderingTest` (403 thật) |
| T8 | XSS | Thymeleaf escape mặc định + CSP `default-src 'self'`, **không** `unsafe-inline`, không inline style | `ErrorPageRenderingTest#securityHeadersAreSent` |
| T9 | CSRF | Token cho mọi form `th:action`; logout POST-only | `SecurityConfigTest` (POST thiếu token → 403), `ErrorPageRenderingTest` (logout sai token → 403) |
| T10 | Session hijacking | Cookie `HttpOnly` (mặc định), session fixation `changeSessionId`, 1 session/tài khoản | `SecurityConfigTest` (matrix + logout), cấu hình trong `SecurityConfig` |
| — | Clickjacking | `X-Frame-Options: DENY` + CSP `frame-ancestors 'none'` | `ErrorPageRenderingTest#securityHeadersAreSent` |

### 9.4. Số liệu để trích dẫn

| Chỉ số | Giá trị |
|---|---|
| Class Java mới trong Phase 3 | 15 — 3 `security/` · 3 `config/` · 1 `dto/` · 3 `validation/` · 2 `exception/` · 1 `service/` · 2 `controller/` |
| Template HTML mới | 7 (3 trang lỗi + 3 trang chính + 1 fragment) |
| CSS viết tay | `app.css` 547 dòng, không framework, không CDN |
| Lớp test mới | 8 lớp / 85 test method |
| Toàn bộ test của dự án sau Phase 3 | 18 lớp / 158 test method |
| Tổng dòng Java (main) | 6.001 dòng / 76 file (trong đó 17 file chỉ là `package-info.java`) |
| Tổng dòng Java (test) | 3.440 dòng / 18 file |
