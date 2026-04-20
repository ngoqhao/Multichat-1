# MultiChat — Java WebSocket Chat

Ứng dụng chat real-time nhiều người dùng, chạy trên web browser, deploy lên Render.

## Cấu trúc project
```
multichat/
├── Dockerfile
├── render.yaml
├── pom.xml
└── src/main/
    ├── java/com/chat/
    │   ├── Main.java                  ← Entry point, khởi động server
    │   ├── ChatRoom.java              ← Logic phòng chat, quản lý users
    │   ├── ChatWebSocketCreator.java  ← WebSocket cho user chat
    │   ├── AdminWebSocketCreator.java ← WebSocket cho admin dashboard
    │   └── AdminServlet.java          ← REST health check
    └── resources/static/
        ├── index.html   ← Giao diện chat cho người dùng
        └── admin.html   ← Dashboard quản lý server
```

---

## DEPLOY LÊN RENDER (Step by step)

### Bước 1 — Đẩy code lên GitHub

```bash
# Tạo repo mới trên github.com, sau đó:
git init
git add .
git commit -m "Initial multichat"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/multichat.git
git push -u origin main
```

### Bước 2 — Tạo Web Service trên Render

1. Vào https://render.com → Đăng nhập
2. Click **New +** → **Web Service**
3. Chọn **Connect a repository** → chọn repo `multichat`
4. Điền thông tin:
   - **Name:** `multichat` (hoặc tên bạn muốn)
   - **Runtime:** **Docker** ← quan trọng
   - **Instance Type:** Free (hoặc Starter)
5. Trong **Environment Variables**, thêm:
   - `PORT` = `8080`
6. Click **Create Web Service**

### Bước 3 — Chờ build & deploy

- Render sẽ tự build Docker image (~3-5 phút)
- Khi status chuyển sang **Live** → deploy thành công
- URL sẽ có dạng: `https://multichat-xxxx.onrender.com`

### Bước 4 — Sử dụng

| URL | Mục đích |
|-----|----------|
| `https://your-app.onrender.com/` | Phòng chat (dành cho người dùng) |
| `https://your-app.onrender.com/admin.html` | Dashboard quản lý (admin) |

---

## Hướng dẫn sử dụng

### Người dùng (index.html)
1. Nhập tên của bạn
2. Nhập mật khẩu phòng (nếu admin đặt)
3. Click **Vào phòng →**
4. Chat bình thường, dùng lệnh `/help` để xem lệnh hỗ trợ

### Lệnh chat
| Lệnh | Mô tả |
|------|-------|
| `/list` | Danh sách người online |
| `/pm <tên> <tin>` | Nhắn tin riêng |
| `/quit` | Thoát phòng |
| `/help` | Xem help |

### Admin (admin.html)
1. **Tab "Mở phòng"**: Cấu hình tên phòng, mật khẩu, welcome message → Click **Áp dụng**
2. **Tab "Quản lý phòng"**: Xem người dùng đang online, kick user, gửi broadcast, xem log

---

## Chạy local (test trước khi deploy)

```bash
# Cần Java 17+ và Maven
mvn package -DskipTests
java -jar target/multichat-1.0.0.jar
# Mở http://localhost:8080
```

---

## Lưu ý

- **Free tier Render** sẽ sleep sau 15 phút không có request — lần đầu load sẽ chậm ~30 giây
- Dữ liệu không lưu vào database — khi restart server, lịch sử chat sẽ mất
- Có thể chạy nhiều tab/browser để test multi-user
