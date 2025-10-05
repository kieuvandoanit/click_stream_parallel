# Giải thích chi tiết thuật toán **CompactSpadeSerial**

## 1. Mục tiêu tổng thể

Thuật toán **CompactSpadeSerial** nhằm tìm tất cả **các mẫu tuần tự có trọng số** (Weighted Sequential Patterns) thỏa mãn ngưỡng hỗ trợ tối thiểu `minWs`. Đây là phiên bản tuần tự (serial) của Compact-SPADE.

**Ý tưởng chính:**

1. Tìm tất cả các **1-pattern** (các hành động đơn lẻ) có hỗ trợ trọng số cao hơn `minWs`.
2. Mở rộng các mẫu này bằng **DFS** (Depth-First Search) dựa trên **các lớp tiền tố (prefix-based classes)**.
3. Kết hợp các mẫu trong cùng một lớp để sinh ra các mẫu dài hơn.
4. Dùng **WCMAP** để loại bỏ sớm các ứng viên yếu (pruning).

---

## 2. Bức tranh hoạt động tổng quát

Hệ thống được chia thành hai pha chính:

### Pha 1: Chuẩn bị dữ liệu

* Tạo **WCMAP**: Bảng đồng xuất hiện có trọng số của các cặp hành động `(x, y)`.
* Xác định tập các hành động xuất hiện trong CSDL (`cdb.alphabet()`).
* Tính **weighted support** cho từng hành động → chọn các hành động đủ ngưỡng tạo thành **F1**.

### Pha 2: Mở rộng mẫu bằng DFS

* Bắt đầu từ lớp rỗng `[£]`, chứa tất cả 1-pattern trong **F1**.
* Trong mỗi lớp:

    * Ghép đôi các mẫu để sinh ứng viên mới theo quy tắc từ phần 4.2 của bài báo.
    * Nếu cặp đuôi của mẫu `(x, y)` có giá trị trong **WCMAP** < `minWs`, loại bỏ ứng viên.
    * Nếu không, tính `weightedSupport(cand)`.
    * Nếu đủ ngưỡng → thêm vào tập kết quả `F` và đưa vào lớp con để tiếp tục mở rộng.
* Đệ quy xuống lớp con (DFS) cho đến khi không sinh thêm được ứng viên nào.

---

## 3. Phân tích chi tiết theo code

### 3.1 Khởi tạo và tìm F1

```java
WCMAP wcmap = WCMAP.build(cdb);
List<List<String>> F1 = new ArrayList<>();
for (String a : cdb.alphabet()) {
    var p = List.of(a);
    double ws = Seqs.weightedSupport(p, cdb);
    if (ws >= minWs) F1.add(p);
}
Set<List<String>> F = new LinkedHashSet<>(F1);
```

* `WCMAP.build(cdb)`: tạo bảng đồng xuất hiện có trọng số.
* `F1`: danh sách các mẫu 1 phần tử có hỗ trợ đủ ngưỡng.
* `F`: tập kết quả chứa tất cả các mẫu tìm được.

### 3.2 Gọi DFS mở rộng theo lớp

```java
dfsExtend(List.of(), F1, F, cdb, wcmap, minWs);
```

* `prefix = []`: lớp gốc `[£]`.
* `patterns = F1`: danh sách mẫu 1 phần tử ban đầu.

### 3.3 Sinh ứng viên trong cùng lớp

```java
for (int i = 0; i < n; i++) {
  for (int j = i; j < n; j++) {
    List<String> p1 = patterns.get(i), p2 = patterns.get(j);
    String a = p1.get(p1.size() - 1), b = p2.get(p2.size() - 1);
    List<List<String>> cands = (p1.equals(p2))
        ? List.of(append(p1, a))
        : List.of(append(p1, b), append(p2, a));
```

* Nếu `p1 == p2` → chỉ sinh 1 ứng viên `(p1, lastP1)`.
* Nếu `p1 ≠ p2` → sinh 2 ứng viên đối xứng `(p1, lastP2)` và `(p2, lastP1)`.

### 3.4 Lọc bằng WCMAP và kiểm tra hỗ trợ trọng số

```java
for (var cand : cands) {
  if (cand.size() >= 2) {
    String x = cand.get(cand.size() - 2), y = cand.get(cand.size() - 1);
    if (wcmap.get(x, y) < minWs) continue; // prune sớm
  }
  double ws = Seqs.weightedSupport(cand, cdb);
  if (ws >= minWs) {
    F.add(cand);
    next.computeIfAbsent(cand.subList(0, cand.size() - 1), k -> new ArrayList<>()).add(cand);
  }
}
```

* Bỏ các mẫu có cặp đuôi yếu trong **WCMAP**.
* Nếu đủ ngưỡng trọng số → thêm vào `F` và đưa vào lớp con tương ứng.

### 3.5 Đệ quy xuống lớp con (DFS)

```java
for (var e : next.entrySet()) {
  dfsExtend(e.getKey(), e.getValue(), F, cdb, wcmap, minWs);
}
```

* Mỗi lớp con được xác định bởi **tiền tố** của các mẫu vừa sinh.
* Đệ quy tiếp tục đến khi không có mẫu mới.

---

## 4. Ví dụ minh họa nhỏ

Giả sử `F1 = {[a], [b], [c], [f]}` và `minWs = 0.4`

### Lớp [£]:

* Ghép (a,b) → `[a,b]`, `[b,a]` → prune `[b,a]` → giữ `[a,b]`
* Ghép (a,c) → `[a,c]`
* Ghép (a,f) → `[a,f]`
* Ghép (b,c) → `[b,c]`
* Ghép (c,f) → `[c,f]`

Kết quả sau bước này:

```
F = {[a], [b], [c], [f], [a,b], [a,c], [a,f], [b,c], [c,f]}
```

Các lớp con:

```
[a] = {[a,b], [a,c], [a,f]}
[b] = {[b,c]}
[c] = {[c,f]}
```

### Lớp [a]:

* Ghép `[a,b]` + `[a,c]` → `[a,b,c]`
* `[a,c,b]` bị prune do WCMAP thấp.

→ Thu được `[a,b,c]`

---

## 5. Các điểm mấu chốt

| Thành phần    | Ý nghĩa                                         |
| ------------- | ----------------------------------------------- |
| **patterns**  | Tập các mẫu trong cùng một lớp tiền tố          |
| **F**         | Tập mẫu toàn cục đã tìm được                    |
| **WCMAP**     | Ma trận đồng xuất hiện có trọng số để prune sớm |
| **dfsExtend** | Hàm đệ quy mở rộng các mẫu theo chiều sâu       |
| **append()**  | Hàm nối thêm phần tử vào cuối mẫu               |

---

## 6. Gợi ý cải tiến khi đọc code

Trong code hiện tại, phần đệ quy nằm bên trong vòng `for (i)`:

```java
for (int i = 0; i < n; i++) {
   for (int j = i; j < n; j++) {...}
   for (var e : next.entrySet()) {
       dfsExtend(...);
   }
}
```

Có thể tách riêng hai pha để dễ hiểu hơn:

```java
for (int i = 0; i < n; i++)
  for (int j = i; j < n; j++)
    ... // sinh next

for (var e : next.entrySet())
  dfsExtend(...);
```

Giúp tách bạch giữa **bước sinh ứng viên** và **bước mở rộng đệ quy**.

---

## 7. Tóm tắt ngắn gọn

* **CompactSpadeSerial** thực hiện khai phá mẫu tuần tự có trọng số bằng **DFS theo lớp tiền tố**.
* **WCMAP** giúp loại bỏ nhanh các mẫu yếu.
* **WeightedSupport** đảm bảo mẫu có ý nghĩa thống kê thực tế.
* **DFS đệ quy** đảm bảo duyệt hết toàn bộ không gian mẫu.
* Đây là nền tảng của các biến thể song song: `StaticPCompactSPADE`, `HPCompactSPADE`, `DPCompactSPADE`, `APCompactSPADE`.
