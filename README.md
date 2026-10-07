# EntityUtils IntelliJ Plugin

**Plugin ID:** `dev.c9tech.intellij-plugin.entity-utils`  
**Compatibility:** IntelliJ IDEA 2022.3+ (build 223+ bao gồm 2023.x, 2024.x, 2025.x như IU-253.x), Java 17

Plugin IntelliJ IDEA hỗ trợ các lập trình viên tự động hóa hoàn toàn quy trình:
1. **Tự tạo DTO từ Entity** (hỗ trợ chọn fields, đổi package, sinh Java Bean / Lombok / Java 17 Record).
2. **Tự tạo class EntityMapper** hoặc chèn methods chuyển đổi 2 chiều giữa Entity & DTO (hỗ trợ null-safety, list converter, merge methods, auto type conversion `Date` <-> `Timestamp`, `String` <-> `Number`).
3. **Thay thế hoàn toàn việc chạy code reflection in console thủ công**, tích hợp sâu vào IntelliJ PSI AST để sinh mã nguồn chuẩn format và tối ưu imports.

---

## 🚀 Các tính năng chính

### 1. Tự tạo DTO từ Entity (Generate DTO from Entity)
- **Phím tắt:** `Ctrl + Alt + D` (hoặc nhấn `Alt + Insert` / Chuột phải -> `EntityUtils` -> `Generate DTO from Entity...`).
- **Giao diện trực quan:**
  - Tự động nhận diện Entity hiện tại và gợi ý tên DTO (`{Entity}Dto` hoặc `{Entity}Bean`).
  - Cho phép tùy chỉnh **Target Package** (mặc định gợi ý subpackage `.dto`).
  - Bảng danh sách toàn bộ field kèm checkbox chọn/bỏ chọn từng field.
  - Các nút tiện ích: **Select All**, **Deselect All**, **Exclude Audit Fields** (loại nhanh các field audit như `id`, `createdAt`, `updatedAt`, `deleteFlg`...).
  - Chọn style sinh code DTO:
    - **Standard Java Bean**: Private fields + Default Constructor + Getters & Setters.
    - **Lombok**: `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`.
    - **Java 17 Record**: `public record XxxDto(...)`.
  - Checkbox **"Generate EntityMapper after DTO creation"**: Tự động mở tiếp dialog tạo Mapper ngay sau khi tạo DTO.

### 2. Tự tạo EntityMapper giữa Entity và DTO (Generate EntityMapper)
- **Phím tắt:** `Ctrl + Alt + M` (hoặc nhấn `Alt + Insert` / Chuột phải -> `EntityUtils` -> `Generate EntityMapper...`).
- **Tính năng Mapper:**
  - **Match fields thông minh:** Tự động ghép nối các getter/setter tương ứng giữa 2 class (nhận diện cả các tên field đặc biệt như `mStatus`, `dScno`, `coilNo`).
  - **Tùy chọn tạo mới Mapper hoặc chèn vào class hiện có:**
    - Tạo mới: Tạo file `{Entity}Mapper.java` hoặc `EntityMapper.java` tại package mong muốn.
    - Chèn vào class có sẵn: Nếu dự án đã có class mapper, plugin sẽ chèn các method mới mà không ghi đè code cũ.
  - **Các phương thức hỗ trợ sinh tự động:**
    - `convertEntityToDto(Entity src)`: Chuyển đổi 1 đối tượng, kiểm tra null an toàn.
    - `convertEntityToDto(List<Entity> srcLst)`: Chuyển đổi danh sách `List`.
    - `convertDtoToEntity(Dto src)`: Chuyển đổi ngược chiều.
    - `convertDtoToEntity(List<Dto> srcLst)`: Chuyển đổi danh sách ngược chiều.
    - `mergeEntity(Dto src, Entity des)`: Cập nhật dữ liệu từ DTO vào Entity có sẵn (rất hữu ích cho các hàm update trong service).
  - **Chuyển đổi kiểu dữ liệu thông minh:**
    - `java.util.Date` $\leftrightarrow$ `java.sql.Timestamp`: tự sinh `new Timestamp(src.getDate().getTime())`.
    - `String` $\leftrightarrow$ `Double` / `Integer` / `Long`: tự sinh `Double.valueOf(...)`, `String.valueOf(...)`.
  - **Quy chuẩn tên method:**
    - Phong cách truyền thống: `convert{Source}To{Target}` (giống code cũ của bạn).
    - Phong cách Fluent: `to{Target}` / `to{Target}List`.

---

## 🛠️ Cấu trúc dự án

```
dev.c9tech.entityutils
 ├── action
 │    ├── GenerateDtoAction.java        # Action xử lý sự kiện tạo DTO
 │    └── GenerateMapperAction.java     # Action xử lý sự kiện tạo Mapper
 ├── generator
 │    ├── DtoGenerator.java             # Bộ sinh mã nguồn class DTO
 │    ├── MapperGenerator.java          # Bộ sinh mã nguồn các method Mapper
 │    └── TypeConversionHelper.java     # Xử lý convert kiểu (Date, Timestamp, Number...)
 ├── model
 │    ├── DtoFieldItem.java             # Model lưu trữ thuộc tính của field DTO
 │    ├── DtoStyle.java                 # Enum Java Bean, Lombok, Record
 │    ├── FieldMappingItem.java         # Model ghép nối giữa Source Field & Target Field
 │    └── MethodNamingStyle.java        # Enum naming style (convertXToY, toTarget)
 ├── ui
 │    ├── ClassChooserUtil.java         # Tiện ích duyệt chọn Class/Package của IntelliJ
 │    ├── GenerateDtoDialog.java        # Giao diện Dialog tạo DTO
 │    └── GenerateMapperDialog.java     # Giao diện Dialog tạo Mapper
 └── util
      ├── PsiClassUtil.java             # Phân tích PSI AST (fields, getters, setters, lombok)
      └── StringUtil.java               # Xử lý chuỗi tên thuộc tính, getter/setter
```

---

## 📦 Cách đóng gói và cài đặt vào IntelliJ IDEA

### 1. Build plugin từ mã nguồn:
Trong thư mục gốc dự án, chạy lệnh:
```bash
./gradlew buildPlugin
```
Sau khi hoàn tất, file plugin ZIP sẽ nằm tại:
```
build/distributions/EntityUtils-1.0.0.zip
```

### 2. Cài đặt vào IntelliJ IDEA:
1. Mở IntelliJ IDEA.
2. Vào **Settings / Preferences** (`Ctrl + Alt + S` hoặc `Cmd + ,`).
3. Chọn mục **Plugins**.
4. Nhấn vào biểu tượng bánh răng ⚙️ ở góc trên bên phải -> Chọn **Install Plugin from Disk...**.
5. Chọn file `build/distributions/EntityUtils-1.0.0.zip` vừa build.
6. Nhấn **Apply** và **Restart IDE** (nếu được yêu cầu).

---

## 💡 Ví dụ mã nguồn sinh ra

Giả sử có Entity `Nyuko`:

```java
// Sinh DTO: NyukoDto.java
public class NyukoDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String mStatus;
    private Timestamp newUpdTime;
    // Getters & Setters...
}
```

Và Mapper tự động `NyukoMapper.java`:

```java
public class NyukoMapper {

    public static NyukoDto convertNyukoToNyukoDto(Nyuko nyuko) {
        if (null == nyuko) {
            return null;
        }
        NyukoDto nyukoDto = new NyukoDto();
        if (null != nyuko.getId()) {
            nyukoDto.setId(nyuko.getId());
        }
        if (null != nyuko.getmStatus()) {
            nyukoDto.setmStatus(nyuko.getmStatus());
        }
        if (null != nyuko.getNewUpdTime()) {
            nyukoDto.setNewUpdTime(nyuko.getNewUpdTime());
        }
        return nyukoDto;
    }

    public static List<NyukoDto> convertNyukoToNyukoDto(List<Nyuko> nyukoLst) {
        if (null == nyukoLst) {
            return null;
        }
        List<NyukoDto> nyukoDtoLst = new ArrayList<>();
        for (Nyuko item : nyukoLst) {
            nyukoDtoLst.add(convertNyukoToNyukoDto(item));
        }
        return nyukoDtoLst;
    }

    public static Nyuko mergeNyuko(NyukoDto src, Nyuko des) {
        if (null == src || null == des) {
            return null;
        }
        if (null != src.getId()) {
            des.setId(src.getId());
        }
        if (null != src.getmStatus()) {
            des.setmStatus(src.getmStatus());
        }
        return des;
    }
}
```
