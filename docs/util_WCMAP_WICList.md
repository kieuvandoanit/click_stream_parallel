# 🧩 Giải thích thành phần `WCMAP` và `WICList`

> Tài liệu tham khảo: *An Efficient Parallel Algorithm for Mining Weighted Clickstream Patterns*  
> Các lớp trong thư mục `org.wcpm.util`  
> Mục tiêu: hiểu cơ chế dữ liệu nền của Compact-SPADE và các biến thể song song.

---

## 1️⃣ WCMAP — Weighted Co-occurrence Map

### 🎯 Mục đích
`WCMAP` (Weighted Co-occurrence Map) là **cấu trúc ánh xạ hai chiều**  
dùng để **ghi lại và tra cứu trọng số hỗ trợ (weighted support)** giữa các cặp hành động `(x, y)`  
xuất hiện **liên tiếp** trong cơ sở dữ liệu clickstream.

Nó được sử dụng trong Compact-SPADE để:
- **Prune sớm (loại bỏ sớm)** các ứng viên không đủ ngưỡng support,  
- Giảm đáng kể thời gian sinh ứng viên ở các mức cao hơn.

---

### ⚙️ Cách hoạt động

1. **Xây dựng (Build Phase)**  
   Khi quét toàn bộ cơ sở dữ liệu clickstream, ta đếm hoặc cộng trọng số cho các cặp liền kề `(x → y)`:

   ```java
   for (clickstream X in CDB)
       for (mỗi cặp (xi, xj) liền kề trong X)
           W(xi, xj) += weight(X)
   ```
2. **Chuẩn hóa**
   Sau khi tính xong, giá trị `W(x, y)` được chuẩn hóa theo **database weight** (tổng trọng số của CDB)
   để trở thành **weighted support (ws)** của cặp `(x, y)`.

3. **Sử dụng khi sinh ứng viên**
   Trong thuật toán (ví dụ `dfsExtend()`):

   ```java
   if (wcmap.get(x, y) < minWs) continue;  // prune sớm ứng viên yếu
   ```

---

### 🧮 Cấu trúc dữ liệu

```java
Map<String, Map<String, Double>> wcmap;
```

* `wcmap[a][b] = 0.65` nghĩa là `(a,b)` có weighted support = 0.65
* Nếu không tồn tại, trả về `0.0`

---

### 📊 Ví dụ minh họa

| Cặp hành động | Weighted Support |
| ------------- | ---------------- |
| (a, b)        | 0.78             |
| (a, c)        | 0.61             |
| (b, f)        | 0.32 ❌ (bị loại) |

Khi sinh ứng viên `(a,b,f)` → vì `W(b,f)=0.32 < 0.4`, ứng viên này bị loại bỏ sớm.

---

### 💡 Tóm tắt

| Thuộc tính        | Giá trị                             |
| ----------------- | ----------------------------------- |
| Viết đầy đủ       | **Weighted Co-occurrence Map**      |
| Vai trò           | Lọc sớm ứng viên khi sinh mẫu mới   |
| Kiểu dữ liệu      | Map<String, Map<String, Double>>    |
| Giai đoạn sử dụng | Candidate Generation (4.3)          |
| Tác dụng          | Giảm khối lượng tính toán và bộ nhớ |

---

## 2️⃣ WICList — Weighted ID-Compact List

### 🎯 Mục đích

`WICList` (Weighted ID-Compact List) là **danh sách dọc (vertical format)**
lưu thông tin về **clickstream ID (sid)** và **vị trí hành động (pos)**,
được **nén** lại thành một số nguyên duy nhất để tiết kiệm bộ nhớ.

Đây là **phiên bản nén** của cấu trúc “IDList” trong SPADE,
được mở rộng cho dữ liệu **weighted clickstream**.

---

### ⚙️ Cách hoạt động

#### 🔹 Encode (nén)

Mỗi cặp `(sid, pos)` được mã hóa thành một `int` duy nhất:

```
cval(sid, pos, dlenbit) = (sid << dlenbit) | pos
```

* `sid`: ID của clickstream (ví dụ 3)
* `pos`: vị trí trong clickstream (ví dụ 14)
* `dlenbit`: số bit cần để biểu diễn vị trí lớn nhất (ví dụ 5 → đủ cho vị trí đến 31)

Ví dụ:

```
sid = 11 (1011)
pos = 14 (1110)
→ cval = 1011 1110 = 366 (decimal)
```

#### 🔹 Decode (giải nén)

```
sid = v >> dlenbit
pos = v & ((1 << dlenbit) - 1)
```

→ Từ `366`, ta lấy lại `(sid=11, pos=14)`.

---

### 🔹 Dùng để làm gì?

Khi join hai pattern, thay vì duyệt từng dòng trong CSDL,
ta chỉ cần **giao hai WICList** để biết các vị trí hợp lệ (nơi mẫu con xảy ra kế tiếp nhau).

Ví dụ:

```
WICList(a) = { (1,1), (2,3) }
WICList(b) = { (1,2), (1,4), (2,4) }

→ join (a,b): tìm nơi 'a' xảy ra trước 'b'
→ new WICList(a,b) = { (1,2), (2,4) }
```

---

### 🧮 Cấu trúc dữ liệu

```java
class WICList {
    List<Integer> compactValues;  // mỗi int mã hóa (sid,pos)
    double weight;                // trọng số trung bình hoặc tổng
}
```

---

### 💡 Tóm tắt

| Thuộc tính        | Giá trị                                     |
| ----------------- | ------------------------------------------- |
| Viết đầy đủ       | **Weighted ID-Compact List**                |
| Vai trò           | Lưu và xử lý dọc (vertical) cho mỗi pattern |
| Kiểu dữ liệu      | List<Integer> (các compact value)           |
| Giai đoạn sử dụng | Pattern Join & Weighted Support Computation |
| Tác dụng          | Nén dữ liệu, giảm bộ nhớ, tăng tốc join     |

---

## 🧠 So sánh tổng hợp

| Thành phần  | Viết đầy đủ                | Vai trò chính                                       | Dạng dữ liệu                     | Vị trí dùng trong thuật toán       |
| ----------- | -------------------------- | --------------------------------------------------- | -------------------------------- | ---------------------------------- |
| **WCMAP**   | Weighted Co-occurrence Map | Lọc sớm ứng viên dựa trên ws(x,y)                   | Map<String, Map<String, Double>> | Khi sinh ứng viên mới              |
| **WICList** | Weighted ID-Compact List   | Lưu danh sách (sid,pos) nén để join và tính support | List<Integer>                    | Khi tính toán & join giữa patterns |

---

### 📘 Liên hệ với thuật toán trong bài báo

| Mục trong paper               | Liên hệ với code                           |
| ----------------------------- | ------------------------------------------ |
| §4.1 WICList                  | Lớp `WICList` trong `util`                 |
| §4.3 WCMAP                    | Lớp `WCMAP` trong `util`                   |
| §5.x Các thuật toán song song | Gọi đến hai lớp này để lưu & prune dữ liệu |

---

### 🧾 Ghi chú thêm

* `WICList` là **dữ liệu cốt lõi** của SPADE (theo hướng *vertical mining*).
* `WCMAP` là **bổ sung mới của Compact-SPADE**, giúp **tránh sinh ứng viên không cần thiết**.
* Hai cấu trúc này **bổ trợ nhau**:

    * `WICList` → nén và lưu vị trí mẫu
    * `WCMAP` → lọc sớm để không sinh mẫu vô ích

---
