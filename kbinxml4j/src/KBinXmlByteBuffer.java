import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class KBinXmlByteBuffer {
    private byte[] data;
    private int offset;
    private ByteOrder endian;
    private int end;

    public byte[] getData() {
        return data;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public KBinXmlByteBuffer() {
        this(new byte[0], 0, ByteOrder.BIG_ENDIAN);
    }

    public KBinXmlByteBuffer(byte[] input) {
        this(input, 0, ByteOrder.BIG_ENDIAN);
    }

    public KBinXmlByteBuffer(byte[] input, int offset) {
        this(input, offset, ByteOrder.BIG_ENDIAN);
    }

    public KBinXmlByteBuffer(byte[] input, int offset, ByteOrder endian) {
        this.data = Arrays.copyOf(input, input.length);
        this.offset = offset;
        this.endian = endian;
        this.end = data.length;
    }

    public KBinXmlByteBuffer(byte[] input, ByteOrder endian) {
        this(input, 0, endian);
    }

    public KBinXmlByteBuffer(String input) {
        this(input.getBytes(StandardCharsets.UTF_8), 0, ByteOrder.BIG_ENDIAN);
    }

    // 获取指定数量的字节
    public byte[] getBytes(int count) {
        if (offset + count > data.length) {
            throw new IndexOutOfBoundsException("Not enough data available");
        }
        byte[] result = Arrays.copyOfRange(data, offset, offset + count);
        offset += count;
        return result;
    }

    // 通用获取方法
    public Object get(String type, Integer count) {
        Object result = peek(type, count);
        int size = getSize(type);
        if (count != null) {
            size *= count;
        }
        offset += size;
        return result;
    }

    // 查看数据但不移动偏移量
    public Object peek(String type, Integer count) {
        int size = getSize(type);
        if (count != null) {
            size *= count;
        }

        if (offset + size > data.length) {
            throw new IndexOutOfBoundsException("Not enough data available");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, size);
        buffer.order(endian);

        switch (type) {
            case "b": // signed byte
                return count == null ? buffer.get() : getByteArray(buffer, count);
            case "B": // unsigned byte
                return count == null ? Byte.toUnsignedInt(buffer.get()) : getUnsignedByteArray(buffer, count);
            case "h": // signed short
                return count == null ? buffer.getShort() : getShortArray(buffer, count);
            case "H": // unsigned short
                return count == null ? Short.toUnsignedInt(buffer.getShort()) : getUnsignedShortArray(buffer, count);
            case "i": // signed int
                return count == null ? buffer.getInt() : getIntArray(buffer, count);
            case "I": // unsigned int
                return count == null ? (int) Integer.toUnsignedLong(buffer.getInt()) : getUnsignedIntArray(buffer, count);
            case "q": // signed long
                return count == null ? buffer.getLong() : getLongArray(buffer, count);
            case "Q": // unsigned long
                // Java中没有无符号long，返回原值
                return count == null ? buffer.getLong() : getLongArray(buffer, count);
            default:
                throw new IllegalArgumentException("Unsupported type: " + type);
        }
    }

    // 添加字节数据
    public void appendBytes(byte[] newData) {
        ensureCapacity(offset + newData.length);
        System.arraycopy(newData, 0, data, offset, newData.length);
        offset += newData.length;
        end = Math.max(end, offset);
    }

    // 通用添加方法
    public void append(Object value, String type, Integer count) {
        int size = getSize(type);
        if (count != null) {
            size *= count;
        }

        ensureCapacity(offset + size);
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.order(endian);

        if (count == null) {
            // 单个值
            putValue(buffer, value, type);
        } else {
            // 数组值
            if (value instanceof Object[] array) {
                for (Object item : array) {
                    putValue(buffer, item, type);
                }
            } else if (value instanceof List array) {
                for (Object item : array) {
                    putValue(buffer, item, type);
                }
            } else {
                throw new IllegalArgumentException("Expected array for count > 1");
            }
        }

        System.arraycopy(buffer.array(), 0, data, offset, size);
        offset += size;
        end = Math.max(end, offset);
    }

    // 设置指定位置的数据
    public void set(Object value, int targetOffset, String type, Integer count) {
        int size = getSize(type);
        if (count != null) {
            size *= count;
        }

        ensureCapacity(targetOffset + size);
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.order(endian);

        if (count == null) {
            putValue(buffer, value, type);
        } else {
            if (value instanceof Object[] array) {
                for (Object item : array) {
                    putValue(buffer, item, type);
                }
            } else if (value instanceof List list) {
                for (Object item : list) {
                    putValue(buffer, item, type);
                }
            } else {
                throw new IllegalArgumentException("Expected array for count > 1");
            }
        }

        System.arraycopy(buffer.array(), 0, data, targetOffset, size);
        offset = Math.max(offset, targetOffset + size);
        end = Math.max(end, targetOffset + size);
    }

    public boolean hasData() {
        return offset < end;
    }

    public void realignWrites(int size) {
        while (data.length % size != 0) {
            appendU8(0);
        }
    }

    public void realignReads(int size) {
        while (offset % size != 0) {
            offset++;
        }
    }

    public int length() {
        return data.length;
    }

    // 各种类型的具体方法
    public byte getS8() {
        return (Byte) get("b", null);
    }

    public byte peekS8() {
        return (Byte) peek("b", null);
    }

    public void appendS8(byte value) {
        append(value, "b", null);
    }

    public void setS8(byte value, int targetOffset) {
        set(value, targetOffset, "b", null);
    }

    public short getS16() {
        return (Short) get("h", null);
    }

    public short peekS16() {
        return (Short) peek("h", null);
    }

    public void appendS16(short value) {
        append(value, "h", null);
    }

    public void setS16(short value, int targetOffset) {
        set(value, targetOffset, "h", null);
    }

    public int getS32() {
        return (Integer) get("i", null);
    }

    public int peekS32() {
        return (Integer) peek("i", null);
    }

    public void appendS32(int value) {
        append(value, "i", null);
    }

    public void setS32(int value, int targetOffset) {
        set(value, targetOffset, "i", null);
    }

    public long getS64() {
        return (Long) get("q", null);
    }

    public long peekS64() {
        return (Long) peek("q", null);
    }

    public void appendS64(long value) {
        append(value, "q", null);
    }

    public void setS64(long value, int targetOffset) {
        set(value, targetOffset, "q", null);
    }

    public int getU8() {
        return (Integer) get("B", null);
    }

    public int peekU8() {
        return (Integer) peek("B", null);
    }

    public void appendU8(int value) {
        append(value, "B", null);
    }

    public void setU8(int value, int targetOffset) {
        set(value, targetOffset, "B", null);
    }

    public int getU16() {
        return (Integer) get("H", null);
    }

    public int peekU16() {
        return (Integer) peek("H", null);
    }

    public void appendU16(int value) {
        append(value, "H", null);
    }

    public void setU16(int value, int targetOffset) {
        set(value, targetOffset, "H", null);
    }

    public int getU32() {
        return (Integer) get("I", null);
    }

    public int peekU32() {
        return (Integer) peek("I", null);
    }

    public void appendU32(int value) {
        append(value, "I", null);
    }

    public void setU32(int value, int targetOffset) {
        set(value, targetOffset, "I", null);
    }

    public long getU64() {
        return (Long) get("Q", null);
    }

    public long peekU64() {
        return (Long) peek("Q", null);
    }

    public void appendU64(long value) {
        append(value, "Q", null);
    }

    public void setU64(long value, int targetOffset) {
        set(value, targetOffset, "Q", null);
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(data, end);
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public ByteOrder getEndian() {
        return endian;
    }

    public void setEndian(ByteOrder endian) {
        this.endian = endian;
    }

    // 辅助方法
    private int getSize(String type) {
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
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            int newCapacity = Math.max(data.length * 2, minCapacity);
            data = Arrays.copyOf(data, newCapacity);
        }
    }

    private void putValue(ByteBuffer buffer, Object value, String type) {
        switch (type) {
            case "b":
                buffer.put((Byte) value);
                break;
            case "B":
                buffer.put((byte) ((Integer) value).intValue());
                break;
            case "h":
                buffer.putShort((Short) value);
                break;
            case "H":
                buffer.putShort((short) ((Integer) value).intValue());
                break;
            case "i":
                buffer.putInt((Integer) value);
                break;
            case "I":
                buffer.putInt((int) value);
                break;
            case "q":
                buffer.putLong((Long) value);
                break;
            case "Q":
                buffer.putLong((Long) value);
                break;
        }
    }

    private byte[] getByteArray(ByteBuffer buffer, int count) {
        byte[] result = new byte[count];
        buffer.get(result);
        return result;
    }

    private byte[] getUnsignedByteArray(ByteBuffer buffer, int count) {
        byte[] result = new byte[count];
        for (int i = 0; i < count; i++) {
            result[i] = (byte) Byte.toUnsignedInt(buffer.get());
        }
        return result;
    }

    private short[] getShortArray(ByteBuffer buffer, int count) {
        short[] result = new short[count];
        for (int i = 0; i < count; i++) {
            result[i] = buffer.getShort();
        }
        return result;
    }

    private int[] getUnsignedShortArray(ByteBuffer buffer, int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = Short.toUnsignedInt(buffer.getShort());
        }
        return result;
    }

    private int[] getIntArray(ByteBuffer buffer, int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = buffer.getInt();
        }
        return result;
    }

    private long[] getUnsignedIntArray(ByteBuffer buffer, int count) {
        long[] result = new long[count];
        for (int i = 0; i < count; i++) {
            result[i] = Integer.toUnsignedLong(buffer.getInt());
        }
        return result;
    }

    private long[] getLongArray(ByteBuffer buffer, int count) {
        long[] result = new long[count];
        for (int i = 0; i < count; i++) {
            result[i] = buffer.getLong();
        }
        return result;
    }
}