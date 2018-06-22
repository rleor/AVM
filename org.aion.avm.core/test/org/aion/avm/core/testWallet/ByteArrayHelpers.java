package org.aion.avm.core.testWallet;


/**
 * Helpers for manipulating byte[] in high-level ways.
 * These are used in a few places for transcoding, as well.  Integer quantities are big-endian-encoded (network order).
 */
public class ByteArrayHelpers {
    public static byte[] appendLong(byte[] array, long l) {
        byte[] longBytes = new byte[] {
                (byte) (0xff & (l >> 56)),
                (byte) (0xff & (l >> 48)),
                (byte) (0xff & (l >> 40)),
                (byte) (0xff & (l >> 32)),
                (byte) (0xff & (l >> 24)),
                (byte) (0xff & (l >> 16)),
                (byte) (0xff & (l >>  8)),
                (byte) (0xff & (l >>  0)),
        };
        byte[] newArray = new byte[array.length + longBytes.length];
        arraycopy(array, 0, newArray, 0, array.length);
        arraycopy(longBytes, 0, newArray, array.length, longBytes.length);
        return newArray;
    }

    public static byte[] concatenate(byte[] one, byte[] two) {
        byte[] result = new byte[one.length + two.length];
        arraycopy(one, 0, result, 0, one.length);
        arraycopy(two, 0, result, one.length, two.length);
        return result;
    }

    public static byte[] encodeInt(int i) {
        byte[] intBytes = new byte[] {
                (byte) (0xff & (i >> 24)),
                (byte) (0xff & (i >> 16)),
                (byte) (0xff & (i >>  8)),
                (byte) (0xff & (i >>  0)),
        };
        return intBytes;
    }

    public static int decodeInt(byte[] fourBytes) {
        return (int)((0xff & fourBytes[0]) << 24)
                | (int)((0xff & fourBytes[1]) << 16)
                | (int)((0xff & fourBytes[2]) << 8)
                | (int)((0xff & fourBytes[3]) << 0);
    }

    public static byte[] encodeLong(long l) {
        byte[] longBytes = new byte[] {
                (byte) (0xff & (l >> 56)),
                (byte) (0xff & (l >> 48)),
                (byte) (0xff & (l >> 40)),
                (byte) (0xff & (l >> 32)),
                (byte) (0xff & (l >> 24)),
                (byte) (0xff & (l >> 16)),
                (byte) (0xff & (l >>  8)),
                (byte) (0xff & (l >>  0)),
        };
        return longBytes;
    }

    public static long decodeLong(byte[] eightBytes) {
        return (long)((0xff & eightBytes[0]) << 56)
                | (long)((0xff & eightBytes[1]) << 48)
                | (long)((0xff & eightBytes[2]) << 40)
                | (long)((0xff & eightBytes[3]) << 32)
                | (long)((0xff & eightBytes[4]) << 24)
                | (long)((0xff & eightBytes[5]) << 16)
                | (long)((0xff & eightBytes[6]) << 8)
                | (long)((0xff & eightBytes[7]) << 0);
    }

    public static void arraycopy(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        for (int i = 0; i < length; ++i) {
            byte elt = src[srcPos + i];
            dest[destPos + i] = elt;
        }
    }

    public static byte[] arraySlice(byte[] src, int srcPos, int length) {
        byte[] slice = new byte[length];
        for (int i = 0; i < length; ++i) {
            byte elt = src[srcPos + i];
            slice[i] = elt;
        }
        return slice;
    }
}
