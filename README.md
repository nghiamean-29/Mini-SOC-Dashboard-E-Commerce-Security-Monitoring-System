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
| 2 | Database (Flyway + JPA entities) | ⬜ Chưa bắt đầu | — |
| 3–14 | Auth → E-Commerce → SOC → Realtime → Simulator → Tests → Docker → Docs | ⬜ Chưa bắt đầu | — |

Theo quy tắc SDD (§20): mỗi lần chỉ thực hiện **một phase**, kết thúc phase phải chạy/test, giải thích và **chờ xác nhận** trước khi sang phase tiếp theo.

---

## 🚀 QUICK START

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
```

### Build và test (không cần database)

```bash
mvn -q clean compile     # gate của Phase 1
mvn test                 # 11 test
mvn -q clean package     # tạo target/mini-soc-dashboard-0.0.1-SNAPSHOT.jar
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

Kiểm tra: <http://localhost:8080/actuator/health> → `{"status":"UP"}`

> ℹ️ **Secret không bao giờ nằm trong repo.** Cấu hình đọc từ biến môi trường
> (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) — xem `config/env/dev.env.example`.
> Profile `prod` **không có** giá trị mặc định cho credential: thiếu biến ⇒ ứng dụng dừng ngay (fail fast).

---

## 🗂️ Tài liệu

| Tài liệu | Nội dung |
|---|---|
| [docs/PHASE-0-PLANNING.md](docs/PHASE-0-PLANNING.md) | Requirements, Actors, Use Cases, Architecture, Database overview, Security architecture, Roadmap, Risks, ADR |
| [docs/PHASE-01-INITIALIZATION.md](docs/PHASE-01-INITIALIZATION.md) | Giải thích `pom.xml`, cấu hình, cấu trúc package, test; 6 khác biệt của Spring Boot 4; cách kiểm chứng |
| [docs/ENVIRONMENT-SETUP.md](docs/ENVIRONMENT-SETUP.md) | Cài JDK/Maven/PostgreSQL, tạo DB theo least privilege, biến môi trường, xử lý sự cố |

`ARCHITECTURE.md`, `DATABASE.md`, `SECURITY.md`, `API.md` sẽ được tạo ở **Phase 14** (theo SDD §14).

---

## 🧱 Cấu trúc project hiện tại (Phase 1)

```
src/main/java/com/yak/minisoc/
├── MiniSocApplication.java      # @SpringBootApplication (entry point)
├── config/         controller/    dto/          entity/     repository/
├── service/        security/      detection/    alert/      incident/
├── audit/          simulator/     exception/    mapper/     validation/
├── websocket/      util/
└──      ↑ mỗi package có package-info.java giải thích trách nhiệm + layer + quy tắc phụ thuộc

src/main/resources/
├── application.yml          # cấu hình chung (secret đọc từ ENV)
├── application-dev.yml      # log SQL, template cache off, simulator bật
├── application-prod.yml     # không có default cho credential, simulator tắt
├── db/migration/            # Flyway (Phase 2)
├── templates/               # Thymeleaf (Phase 3+)
└── static/{css,js,vendor}/  # HTML/CSS/JS thuần (không framework JS)
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
