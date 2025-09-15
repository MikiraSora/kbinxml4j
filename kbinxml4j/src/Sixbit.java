import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public class Sixbit {
    private static final String CHARMAP = "0123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";
    private static final Map<Character, Integer> BYTEMAP = new HashMap<>();

    static {
        for (int i = 0; i < CHARMAP.length(); i++) {
            BYTEMAP.put(CHARMAP.charAt(i), i);
        }
    }

    public static void packSixBit(String str, KBinXmlByteBuffer byteBuf) {
        int[] chars = new int[str.length()];
        for (int i = 0; i < str.length(); i++) {
            chars[i] = BYTEMAP.get(str.charAt(i));
        }

        int padding = 8 - (str.length() * 6 % 8);
        if (padding == 8) {
            padding = 0;
        }

        var bits = BigInteger.ZERO;
        for (int c : chars) {
            bits = bits.shiftLeft(6);
            bits = bits.or(BigInteger.valueOf(c));
        }
        bits = bits.shiftLeft(padding);

        int totalBits = str.length() * 6 + padding;
        int totalBytes = totalBits / 8;

        byte[] data = new byte[totalBytes];
        for (int i = totalBytes - 1; i >= 0; i--) {
            data[i] = (byte) (bits.and(BigInteger.valueOf(0xFF)).intValue());
            bits = bits.shiftRight(8);
        }

        byteBuf.appendBytes(new byte[]{(byte) str.length()});

        byteBuf.appendBytes(data);
    }

    private static long bytesToBigEndian(byte[] bytes) {
        if (bytes == null || bytes.length < 0)
            return -1;

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.BIG_ENDIAN);
        return buffer.getInt();
    }

    public static String unpackSixbit(KBinXmlByteBuffer byteBuf) {
        int length = byteBuf.getU8();
        int lengthBits = length * 6;
        int lengthBytes = (lengthBits + 7) / 8;
        int padding = 8 - (lengthBits % 8);
        if (padding == 8) {
            padding = 0;
        }

        byte[] raw = byteBuf.getBytes(lengthBytes);
        BigInteger bits = BigInteger.ZERO;
        for (byte b : raw) {
            bits = bits.shiftLeft(8).or(BigInteger.valueOf(b & 0xFF));
        }
        bits = bits.shiftRight(padding);

        char[] result = new char[length];
        for (int i = length - 1; i >= 0; i--) {
            int ch = bits.and(BigInteger.valueOf(0b111111)).intValue();
            result[i] = CHARMAP.charAt(ch);
            bits = bits.shiftRight(6);
        }
        return new String(result);
    }
}
