package org.openjdk.jol.util;

import java.util.Arrays;


public class ArrayReuse {

    public static class Objects {
        private static final Object[] OBJECT_SEED = new Object[1 << 9];

        public static void clear( Object[] array ) {
            clear(array, array.length);
        }

        public static void clear( Object[] array, int usedLength ) {
            if (usedLength < 1 << 5) {
                Arrays.fill(array, 0, usedLength, null);
            } else {
                ArrayReuse.clear(array, usedLength, OBJECT_SEED, OBJECT_SEED.length);
            }
        }
    }

    @SuppressWarnings("SuspiciousSystemArraycopy")
    static void clear( Object array, int len, Object seed, int slen ) {
        int initLen = Math.min(len, slen); // clear as far as the seed takes up
        System.arraycopy(seed, 0, array, 0, initLen);

        int i; // clear the bulk in a logarithmic number of internal copies
        for (i = initLen; i + i < len; i += i) {
            System.arraycopy(array, 0, array, i, i);
        }

        if (len > i) { // clear the tail
            System.arraycopy(array, 0, array, i, len - i);
        }
    }
}
