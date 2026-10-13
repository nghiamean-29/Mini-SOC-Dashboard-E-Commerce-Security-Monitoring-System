# SQL validation harness — Mini SOC

> Công cụ **kiểm chứng lớp SQL** (9 file Flyway) bằng một **PostgreSQL thật**, chạy trong Node.js
> qua [PGlite](https://github.com/electric-sql/pglite) (PostgreSQL biên dịch sang WebAssembly).
> Đây là **bằng chứng bổ sung** cho Phase 2, chạy được mà **không cần cài PostgreSQL**.

---

## 1. Vì sao cần công cụ này?

`mvn test` chạy các **JPA slice test** — chúng kiểm tra *entity ↔ bảng* và *câu query* có trả đúng dữ liệu không.
Nhưng có một câu hỏi mà slice test **không** trả lời được:

> *"Chính các file migration `V*.sql` chạy được trên PostgreSQL và các ràng buộc (CHECK/UNIQUE/FK)
> có thực sự **từ chối** dữ liệu xấu không?"*

- Khi ứng dụng khởi động, Flyway mới chạy migration — nếu script sai, bạn chỉ phát hiện lúc đó.
- Các ràng buộc như `ck_orders_cancelled`, `uk_carts_user`, `ck_security_events_type` chỉ chứng minh
  được giá trị khi **thử insert dữ liệu sai** và thấy database từ chối.

Harness này làm đúng hai việc đó, trong vài giây, trên PostgreSQL 18.3.

**Phân chia nhiệm vụ (không trùng lặp):**

| Lớp kiểm chứng | Công cụ | Kiểm cái gì |
|---|---|---|
| Mapping & query | `mvn test` (`@DataJpaTest`) | Entity có đúng bảng/cột không, query trả đúng dữ liệu không, JSONB round-trip |
| DDL & ràng buộc | **harness này** | Migration chạy được không, CHECK/UNIQUE/FK có từ chối dữ liệu xấu không, luồng SOC đầu–cuối |

---

## 2. Chạy

```bash
cd tools/sql-validation
npm install        # chỉ lần đầu: tải PGlite (~ vài MB)
node validate.mjs  # hoặc: npm run validate
```

- Exit code **0** = tất cả check PASS; **1** = có check FAIL (in ra danh sách check hỏng → dùng được trong CI).
- Không cần PostgreSQL, không cần Docker, không cần Maven.
- Node.js ≥ 18 (khuyến nghị 20+).

Kết quả mong đợi (rút gọn):

```
=== Mini SOC - SQL validation on PostgreSQL 18.3 (PGlite 0.5.8) ===
1) Applying Flyway migrations
  ok    V1__create_users_and_roles.sql
  ...
  ok    V9__seed_reference_data.sql
...
=== 64 passed, 0 failed ===
```

---

## 3. Harness kiểm tra những gì?

| # | Nhóm | Nội dung |
|---|---|---|
| 1 | **Migrations** | Cả 9 file `V1..V9` apply thành công, theo đúng thứ tự tên file |
| 2 | **Schema** | 17 bảng ứng dụng, số index, số ràng buộc, và sự tồn tại của các index báo cáo SOC (`idx_security_events_metadata_gin`, `idx_security_alerts_open`, `idx_products_name_lower`, …) |
| 3 | **Sequence** | `order_code_seq`, `incident_code_seq` tồn tại và `nextval` luôn tăng |
| 4 | **Seed (V9)** | Đúng 3 role `CUSTOMER/ADMIN/SOC_ANALYST`; **không** seed user nào (không có mật khẩu mặc định) |
| 5 | **Ràng buộc** | ~20 lần cố tình insert/update dữ liệu sai và yêu cầu database **phải từ chối**: username sai định dạng, status lạ, trùng username, gán role 2 lần, login thất bại thiếu lý do, slug sai, stock âm, 2 cart cho 1 user, trùng dòng cart, quantity > 999, `CANCELLED` thiếu `cancelled_at`, state lạ, quantity đơn hàng = 0, xóa user đang có đơn (FK RESTRICT), event type lạ, status code 999, method lạ, trùng `dedup_key`, `related_event_count` = 0, trùng mã incident, `RESOLVED` thiếu `resolved_at`, gắn alert 2 lần, ghi chú rỗng |
| 6 | **JSONB** | Ghi `metadata` dạng JSON, đọc lại theo key, và truy vấn `metadata @> '{"simulated": true}'` (đúng kiểu truy vấn mà index GIN phục vụ) |
| 7 | **Luồng SOC** | order → event → alert → incident → note → resolve, rồi đọc lại toàn bộ chuỗi từ database |
| 8 | **Retention** | Câu `DELETE ... WHERE occurred_at < ...` chạy được (đường đi của job dọn dữ liệu Phase 9) |

> Harness cố tình **không** hardcode id (`1`, `2`, …): PostgreSQL vẫn tiêu tốn giá trị identity cho
> những INSERT bị CHECK từ chối, nên id thật không đoán được. Mọi chỗ đều dùng `RETURNING id`
> hoặc tra theo khóa tự nhiên (`username`, `slug`, `dedup_key`).

---

## 4. Có thay thế được `mvn test` không?

**Không** — và cũng không nhằm thay thế:

- Harness hiểu **SQL**, không hiểu Java: nó không biết entity `Order` map cột nào, hay
  `CartRepository.findWithItemsByUserId` có N+1 hay không.
- `mvn test` hiểu **Java**, nhưng không chạy migration và không thử dữ liệu xấu ở tầng database.
- Hai công cụ trả lời hai câu hỏi khác nhau; khi cả hai cùng xanh thì lớp dữ liệu mới thực sự đáng tin.

---

## 5. Thêm migration mới thì làm gì?

1. Viết file `V10__...sql` trong `src/main/resources/db/migration/`.
2. Chạy lại harness: `node validate.mjs` — nó tự đọc **mọi** file `V*.sql` theo thứ tự.
3. Nếu thêm bảng/ràng buộc quan trọng, thêm một check tương ứng vào `validate.mjs`
   (mục 2, 5 hoặc 7) để lần sau có người phá thì test đỏ ngay.

---

## 6. Ghi chú kỹ thuật

- PGlite được **ghim chính xác** ở `0.5.8` (chứa PostgreSQL 18.3) để kết quả kiểm chứng có thể lặp lại;
  đổi version PGlite là đổi engine PostgreSQL, phải chạy lại và ghi lại kết quả.
- `node_modules/` và `package-lock.json` **không** commit (xem `.gitignore`).
- Harness dùng `db.exec()` cho cả file migration (nhiều câu lệnh, có `DO $$ ... $$` của V9).
