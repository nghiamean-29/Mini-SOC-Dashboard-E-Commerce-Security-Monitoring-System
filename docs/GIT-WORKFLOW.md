# GIT WORKFLOW — Đồng bộ code về máy

> Tài liệu này trả lời một câu hỏi rất cụ thể: **làm sao để máy bạn luôn có code mới nhất mà tôi viết?**
>
> Ngắn gọn: **tôi push lên GitHub → bạn `git pull`.**
> Mọi thay đổi đều đi qua nhánh `arena/01a0c33c-mini-soc-dashboard-e-commerce`.

---

## 0. BỐI CẢNH: code đi từ đâu tới đâu

```
   Môi trường làm việc của tôi (sandbox)          GitHub                    Máy của bạn
   ┌───────────────────────────────┐        ┌──────────────────┐      ┌──────────────────┐
   │ tôi viết code + commit        │ push   │ nhánh:           │ pull │ bạn chạy         │
   │ (pom.xml, src/, docs/...)     │ ─────▶ │ arena/01a0c33c-  │ ───▶ │ mvn test         │
   │                               │        │ mini-soc-...     │      │ mvn spring-boot:run
   └───────────────────────────────┘        └──────────────────┘      └──────────────────┘
```

* **Nhánh `main`**: hiện chỉ có 1 commit khởi tạo. Dùng cho việc **nộp bài / bản chính thức** (Phase 14).
* **Nhánh `arena/01a0c33c-mini-soc-dashboard-e-commerce`**: **nhánh làm việc thật**. Mọi code của dự án nằm ở đây.

> ⚠️ **Một điều đã xảy ra và bạn nên biết:** môi trường sandbox có thể **khôi phục lại thư mục `.git`** giữa các phiên làm việc. Điều này đã xảy ra một lần: **file trên đĩa vẫn còn nhưng 3 commit chưa push thì bị mất** (tôi đã tạo lại và push — nội dung code giống hệt, chỉ gộp lịch sử commit từ 3 thành 2).
>
> **↳ Quy tắc rút ra từ sự việc đó:** mọi phase đều phải **push lên GitHub** trước khi tôi kết thúc lượt. Từ Phase 1 trở đi tôi sẽ luôn push và **báo bạn mã commit (SHA 7 ký tự)** để bạn đối chiếu.

---

## 1. LẦN ĐẦU TIÊN — lấy code về máy

### 1.1. Nếu máy bạn **chưa** có thư mục dự án

```bash
git clone https://github.com/nghiamean-29/Mini-SOC-Dashboard-E-Commerce-Security-Monitoring-System.git
cd Mini-SOC-Dashboard-E-Commerce-Security-Monitoring-System
git checkout arena/01a0c33c-mini-soc-dashboard-e-commerce
```

> Tên nhánh dài, mỗi lần gõ mệt. Có thể đặt **tên ngắn địa phương** cho nhánh này (tuỳ chọn):
> ```bash
> git checkout -b dev arena/01a0c33c-mini-soc-dashboard-e-commerce
> ```
> Từ đó `git pull` trên nhánh `dev` vẫn lấy code mới từ nhánh arena. Chỉ là tên gọi, không ảnh hưởng nội dung.

**Windows PowerShell** (nếu chưa có Git): cài bằng `winget install Git.Git`, sau đó mở **Git Bash** hoặc PowerShell và chạy y hệt các lệnh trên.

### 1.2. Kiểm tra đã lấy đúng chưa

```bash
git log --oneline -3      # phải thấy commit "feat(phase-1): initialize Spring Boot 4.1 project..."
git rev-parse --short HEAD   # in ra ví dụ: f024187  <- so với SHA tôi báo
ls                        # phải thấy: pom.xml  src/  docs/  config/  README.md
```

✅ **Bằng chứng duy nhất đáng tin là SHA commit.** Nếu SHA khớp với SHA tôi báo → code trên máy bạn đúng bằng code tôi viết.

### 1.3. Tạo file secret (chỉ làm **một lần**, file này không bao giờ bị `pull` ghi đè)

```bash
# Linux / macOS
cp config/env/dev.env.example config/env/dev.env
# rồi sửa DB_PASSWORD trong config/env/dev.env thành mật khẩu thật của bạn

# Windows PowerShell
Copy-Item config\env\dev.env.example config\env\dev.env
notepad config\env\dev.env
```

> `config/env/dev.env` nằm trong `.gitignore` ⇒ Git **không** quản lý nó ⇒ `git pull` **không bao giờ** động vào, và bạn cũng không vô tình commit mật khẩu. Đây là lý do ta tách "file mẫu (commit được)" khỏi "file thật (không commit)".

---

## 2. MỖI LẦN CẦN CẬP NHẬT — chỉ một lệnh

Khi tôi báo *"đã push, Phase X xong"*:

```bash
git pull
```

Xong. Vì nhánh của bạn đã "theo dõi" (tracking) nhánh trên GitHub, `git pull` = `git fetch` + `git merge`, và vì bạn không sửa gì nên nó luôn **fast-forward** (không bao giờ sinh conflict).

**Muốn an toàn tuyệt đối (khuyến nghị dùng lâu dài):**

```bash
git pull --ff-only
```
`--ff-only` nghĩa là: *"chỉ cập nhật nếu làm được mà không cần merge"*. Nếu có gì bất thường, Git sẽ **dừng lại và báo** thay vì tự tạo một merge commit rối. Với người mới, đây là chế độ an toàn nhất.

**Kiểm tra sau khi pull:**

```bash
git log --oneline -3        # xem 3 commit mới nhất
git status                  # "working tree clean" = máy bạn khớp GitHub 100%
```

---

## 3. NHỮNG TÌNH HUỐNG THƯỜNG GẶP

| Tình huống | Lệnh / Cách xử lý |
|---|---|
| **Bạn chỉ muốn xem có gì mới, chưa muốn cập nhật** | `git fetch origin` rồi `git log --oneline HEAD..origin/arena/01a0c33c-mini-soc-dashboard-e-commerce` (liệt kê commit mới mà bạn chưa có) |
| **Bạn muốn xem chi tiết một commit** | `git show f024187 --stat` (danh sách file) hoặc `git show f024187 -- pom.xml` (nội dung của 1 file) |
| **Bạn lỡ sửa file mà tôi cũng sửa → `git pull` báo lỗi** | Cất thay đổi của bạn đi, pull xong lấy lại: `git stash` → `git pull --ff-only` → `git stash pop`. Nếu xung đột không quan trọng: `git checkout -- <file>` (bỏ thay đổi ở file đó) |
| **Bạn muốn **bỏ hết** thay đổi địa phương, lấy đúng bản trên GitHub** | `git fetch origin` → `git reset --hard origin/arena/01a0c33c-mini-soc-dashboard-e-commerce` ⚠️ **mất mọi thay đổi chưa commit trên máy** |
| **`git pull` báo "diverged" / "non-fast-forward"** | Nguyên nhân: bạn có commit địa phương mà GitHub không có. Xem bằng `git log --oneline origin/arena/...  ^HEAD` — nếu commit đó không cần nữa: `git reset --hard origin/arena/...` |
| **Git báo "detached HEAD"** | `git switch arena/01a0c33c-mini-soc-dashboard-e-commerce` |
| **Máy bạn không có quyền push** | Bình thường — **bạn không cần push**. Chỉ cần `pull`. Việc push là của tôi và đã cấu hình sẵn |
| **Repo private/đổi quyền, clone hỏi đăng nhập** | Repo hiện đang **public** nên `clone`/`pull` không cần đăng nhập. Nếu bạn đổi sang private: dùng **Personal Access Token** hoặc SSH key (không dùng mật khẩu GitHub — GitHub đã bỏ hỗ trợ đó) |
| **Mạng công ty/trường chặn GitHub** | Đặt proxy trong `git config --global http.proxy ...`, hoặc dùng hotspot điện thoại |

### 3.1. Quy tắc vàng để **không bao giờ** gặp conflict

1. **Đừng sửa những file tôi đang viết** (`pom.xml`, `src/**`, `docs/PHASE-*.md`) — vì hai bên sửa cùng file là nguồn conflict duy nhất.
2. **Muốn ghi chú/sửa bài thì tạo file riêng của bạn**, ví dụ `docs/NOTES-CUA-BAN.md` hoặc `docs/CAU-HOI.md`, rồi nói tôi — tôi sẽ không chạm vào file đó:

```bash
# tên file theo quy ước riêng để tránh bị tôi ghi đè
touch docs/MY-NOTES.md            # Windows: New-Item docs\MY-NOTES.md
```
   (Khi bạn muốn đưa ghi chú vào bản chính thức thì nói tôi, tôi sẽ tổng hợp vào tài liệu chung.)
3. **Trước khi pull, xem mình có sửa gì không:** `git status` → nếu thấy dòng `modified:` là bạn đang có thay đổi.
4. **Không bao giờ** chạy `git push --force`, `git reset --hard` khi chưa chắc (xem cảnh báo ở bảng trên).

---

## 4. QUY TRÌNH MẪU MỘT BUỔI LÀM VIỆC

```bash
# ── 1. Bắt đầu buổi: lấy code mới nhất
cd Mini-SOC-Dashboard-E-Commerce-Security-Monitoring-System
git pull --ff-only
git log --oneline -1          # ghi lại SHA để đối chiếu với SHA tôi báo

# ── 2. Làm việc: chạy thử theo hướng dẫn của phase
mvn -q clean compile
mvn test

# ── 3. Nếu cần secret (một lần): nạp biến môi trường
set -a; source config/env/dev.env; set +a      # Windows: xem mục 1.3

# ── 4. Chạy ứng dụng
mvn spring-boot:run
```

**Mẹo Windows — tạo lệnh tắt cho cả quy trình** (tuỳ chọn). Mở `notepad $PROFILE` và thêm:

```powershell
function sync-minisoc {
    Set-Location "$HOME\Mini-SOC-Dashboard-E-Commerce-Security-Monitoring-System"
    git pull --ff-only
    git log --oneline -3
}
```
Mở PowerShell mới, gõ `sync-minisoc` là xong bước 1. (macOS/Linux: thêm `alias sync-minisoc='cd ~/Mini-... && git pull --ff-only && git log --oneline -3'` vào `~/.bashrc` hoặc `~/.zshrc`.)

---

## 5. NHÁNH `main` — dùng khi nào?

| | Nhánh `arena/01a0c33c-...` | Nhánh `main` |
|---|---|---|
| Nội dung | Code đang phát triển, cập nhật mỗi phase | Hiện chỉ có commit khởi tạo |
| Dùng cho | Học, chạy thử, sửa lỗi cùng tôi | **Nộp bài / bản chính thức** |

**Cách đưa code lên `main` khi cần:** yêu cầu tôi *"tạo PR arena → main"* — tôi sẽ mở Pull Request trên GitHub với mô tả đầy đủ, rồi **bạn** bấm merge (hoặc bảo tôi merge). Pull Request này cũng là **bằng chứng quy trình làm việc** bạn có thể đưa vào báo cáo: có nhánh, có commit message theo chuẩn Conventional Commits, có review.

> **Lưu ý về môi trường:** sandbox đôi khi khôi phục `.git`. Nếu một hôm bạn `git pull` và **không** thấy commit mới nào dù tôi báo đã push → nói tôi ngay, tôi sẽ kiểm tra lại bằng GitHub API và push lại.

---

## 6. KIỂM TRA NHANH "MÁY TÔI ĐANG Ở ĐÂU"?

```bash
git log --oneline -1                 # commit hiện tại
git status -sb                       # ví dụ: ## arena/01a0c33c-... [ahead 0, behind 0]
git remote -v                        # xác nhận đang nói chuyện với repo nào
git fetch origin && git status -sb   # nếu hiện "[behind N]" => cần git pull
```

`[behind 0]` (hoặc không có `[behind ...]`) = máy bạn đã là bản mới nhất. 🎉

---

## 7. TÓM TẮT 3 DÒNG

1. **Lần đầu:** `git clone` → `git checkout arena/01a0c33c-mini-soc-dashboard-e-commerce`.
2. **Mỗi lần tôi báo push:** `git pull --ff-only`.
3. **Kiểm chứng:** `git rev-parse --short HEAD` phải khớp SHA tôi báo trong tin nhắn.
