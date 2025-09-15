import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class FormatIds {
    // IP地址解析和写入方法
    public static long parseIP(String ipString) {
        String[] parts = ipString.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IP address format: " + ipString);
        }

        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < 4; i++) {
            buffer.put((byte) Integer.parseInt(parts[i]));
        }
        buffer.flip();
        return Integer.toUnsignedLong(buffer.getInt());
    }

    public static String writeIP(Object raw) {
        var value = (long) raw;
        return String.format("%d.%d.%d.%d",
                (value >> 24) & 0xFF,
                (value >> 16) & 0xFF,
                (value >> 8) & 0xFF,
                value & 0xFF);
    }

    public static String writeFloat(Object value) {
        return String.format("%.6f", value);
    }

    // XML格式定义类
    public static class XmlFormat {
        public final int id;
        public final String type;
        public final int count;
        public final String[] names;
        public final Function<String, Object> fromStr;
        public final Function<Object, String> toStr;
        public final String name;

        public XmlFormat(int id, String type, int count, String[] names,
                         Function<String, Object> fromStr, Function<Object, String> toStr) {
            this.id = id;
            this.type = type;
            this.count = count;
            this.names = names;
            this.fromStr = fromStr;
            this.toStr = toStr;
            this.name = names.length > 0 ? names[0] : "";
        }
    }

    // XML格式映射
    public static final Map<Integer, XmlFormat> XML_FORMATS = new HashMap<>();
    public static final Map<String, Integer> XML_TYPES = new HashMap<>();

    static {
        // 初始化XML格式映射
        XML_FORMATS.put(1, new XmlFormat(1, null, -1, new String[]{"void"}, null, null));
        XML_FORMATS.put(2, new XmlFormat(2, "b", 1, new String[]{"s8"}, o -> Byte.valueOf(o), o -> String.valueOf(o)));
        XML_FORMATS.put(3, new XmlFormat(3, "B", 1, new String[]{"u8"}, o -> Byte.valueOf(o), o -> String.valueOf(Byte.toUnsignedInt(((Number) o).byteValue()))));
        XML_FORMATS.put(4, new XmlFormat(4, "h", 1, new String[]{"s16"}, o -> Short.valueOf(o), o -> String.valueOf(o)));
        XML_FORMATS.put(5, new XmlFormat(5, "H", 1, new String[]{"u16"}, o -> Short.valueOf(o), o -> String.valueOf(Integer.toUnsignedLong(((Number) o).intValue()))));
        XML_FORMATS.put(6, new XmlFormat(6, "i", 1, new String[]{"s32"}, o -> Integer.valueOf(o), o -> String.valueOf(o)));
        XML_FORMATS.put(7, new XmlFormat(7, "I", 1, new String[]{"u32"}, o -> Integer.valueOf(o), o -> String.valueOf(Integer.toUnsignedLong(((Number) o).intValue()))));
        XML_FORMATS.put(8, new XmlFormat(8, "q", 1, new String[]{"s64"}, o -> Long.valueOf(o), o -> String.valueOf(o)));
        XML_FORMATS.put(9, new XmlFormat(9, "Q", 1, new String[]{"u64"}, o -> Long.valueOf(o), o -> String.valueOf(Integer.toUnsignedLong(((Number) o).intValue()))));
        XML_FORMATS.put(10, new XmlFormat(10, "B", -1, new String[]{"bin", "binary"}, null, o -> String.valueOf(o)));
        XML_FORMATS.put(11, new XmlFormat(11, "B", -1, new String[]{"str", "string"}, o -> String.valueOf(o), o -> String.valueOf(o)));
        XML_FORMATS.put(12, new XmlFormat(12, "I", 1, new String[]{"ip4"}, FormatIds::parseIP, FormatIds::writeIP));
        XML_FORMATS.put(13, new XmlFormat(13, "I", 1, new String[]{"time"}, o -> Integer.valueOf(o), o -> String.valueOf(o)));
        XML_FORMATS.put(14, new XmlFormat(14, "f", 1, new String[]{"float", "f"}, Double::parseDouble, FormatIds::writeFloat));
        XML_FORMATS.put(15, new XmlFormat(15, "d", 1, new String[]{"double", "d"}, Double::parseDouble, FormatIds::writeFloat));
        XML_FORMATS.put(16, new XmlFormat(16, "b", 2, new String[]{"2s8"}, null, null));
        XML_FORMATS.put(17, new XmlFormat(17, "B", 2, new String[]{"2u8"}, null, null));
        XML_FORMATS.put(18, new XmlFormat(18, "h", 2, new String[]{"2s16"}, null, null));
        XML_FORMATS.put(19, new XmlFormat(19, "H", 2, new String[]{"2u16"}, null, null));
        XML_FORMATS.put(20, new XmlFormat(20, "i", 2, new String[]{"2s32"}, null, null));
        XML_FORMATS.put(21, new XmlFormat(21, "I", 2, new String[]{"2u32"}, null, null));
        XML_FORMATS.put(22, new XmlFormat(22, "q", 2, new String[]{"2s64", "vs64"}, null, null));
        XML_FORMATS.put(23, new XmlFormat(23, "Q", 2, new String[]{"2u64", "vu64"}, null, null));
        XML_FORMATS.put(24, new XmlFormat(24, "f", 2, new String[]{"2f"}, Double::parseDouble, FormatIds::writeFloat));
        XML_FORMATS.put(25, new XmlFormat(25, "d", 2, new String[]{"2d", "vd"}, Double::parseDouble, FormatIds::writeFloat));
        XML_FORMATS.put(26, new XmlFormat(26, "b", 3, new String[]{"3s8"}, null, null));
        XML_FORMATS.put(27, new XmlFormat(27, "B", 3, new String[]{"3u8"}, null, null));
        XML_FORMATS.put(28, new XmlFormat(28, "h", 3, new String[]{"3s16"}, null, null));
        XML_FORMATS.put(29, new XmlFormat(29, "H", 3, new String[]{"3u16"}, null, null));
        XML_FORMATS.put(30, new XmlFormat(30, "i", 3, new String[]{"3s32"}, null, null));
        XML_FORMATS.put(31, new XmlFormat(31, "I", 3, new String[]{"3u32"}, null, null));
        XML_FORMATS.put(32, new XmlFormat(32, "q", 3, new String[]{"3s64"}, null, null));
        XML_FORMATS.put(33, new XmlFormat(33, "Q", 3, new String[]{"3u64"}, null, null));
        XML_FORMATS.put(34, new XmlFormat(34, "f", 3, new String[]{"3f"}, Double::parseDouble, FormatIds::writeFloat));
        XML_FORMATS.put(35, new XmlFormat(35, "d", 3, new String[]{"3d"}, Double::parseDouble, FormatIds::writeFloat));
        XML_FORMATS.put(36, new XmlFormat(36, "b", 4, new String[]{"4s8"}, null, null));
        XML_FORMATS.put(37, new XmlFormat(37, "B", 4, new String[]{"4u8"}, null, null));
        XML_FORMATS.put(38, new XmlFormat(38, "h", 4, new String[]{"4s16"}, null, null));
        XML_FORMATS.put(39, new XmlFormat(39, "H", 4, new String[]{"4u16"}, null, null));
        XML_FORMATS.put(40, new XmlFormat(40, "i", 4, new String[]{"4s32", "vs32"}, null, null));
        XML_FORMATS.put(41, new XmlFormat(41, "I", 4, new String[]{"4u32", "vu32"}, null, null));
        XML_FORMATS.put(42, new XmlFormat(42, "q", 4, new String[]{"4s64"}, null, null));
        XML_FORMATS.put(43, new XmlFormat(43, "Q", 4, new String[]{"4u64"}, null, null));
        XML_FORMATS.put(44, new XmlFormat(44, "f", 4, new String[]{"4f", "vf"}, Double::parseDouble, FormatIds::writeFloat));
        XML_FORMATS.put(45, new XmlFormat(45, "d", 4, new String[]{"4d"}, Double::parseDouble, FormatIds::writeFloat));
        XML_FORMATS.put(46, new XmlFormat(46, null, -1, new String[]{"attr"}, null, null));
        XML_FORMATS.put(48, new XmlFormat(48, "b", 16, new String[]{"vs8"}, null, null));
        XML_FORMATS.put(49, new XmlFormat(49, "B", 16, new String[]{"vu8"}, null, null));
        XML_FORMATS.put(50, new XmlFormat(50, "h", 8, new String[]{"vs16"}, null, null));
        XML_FORMATS.put(51, new XmlFormat(51, "H", 8, new String[]{"vu16"}, null, null));
        XML_FORMATS.put(52, new XmlFormat(52, "b", 1, new String[]{"bool", "b"}, o -> {
            var str = o.toString();
            return Byte.valueOf(str);
        }, o -> String.valueOf(o)));
        XML_FORMATS.put(53, new XmlFormat(53, "b", 2, new String[]{"2b"}, null, null));
        XML_FORMATS.put(54, new XmlFormat(54, "b", 3, new String[]{"3b"}, null, null));
        XML_FORMATS.put(55, new XmlFormat(55, "b", 4, new String[]{"4b"}, null, null));
        XML_FORMATS.put(56, new XmlFormat(56, "b", 16, new String[]{"vb"}, null, null));

        // 构建类型名称到ID的映射
        for (Map.Entry<Integer, XmlFormat> entry : XML_FORMATS.entrySet()) {
            XmlFormat format = entry.getValue();
            for (String name : format.names) {
                XML_TYPES.put(name, format.id);
            }
        }

        // 添加特殊类型
        XML_TYPES.put("nodeStart", 1);
        XML_TYPES.put("nodeEnd", 190);
        XML_TYPES.put("endSection", 191);
    }

    // 辅助方法：获取类型大小
    public static int getTypeSize(String type) {
        if (type == null) return 0;
        switch (type) {
            case "b":
            case "B":
                return 1;
            case "h":
            case "H":
                return 2;
            case "i":
            case "I":
                return 4;
            case "q":
            case "Q":
                return 8;
            case "f":
                return 4;
            case "d":
                return 8;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }
}
