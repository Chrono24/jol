/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.jol.util;

import java.util.Arrays;


/**
 * Identity hash set implementation optimized for JOL uses. Cuts corners where it can.
 */
public final class SimpleIdentityHashSet {
    private static final int DEFAULT_SCALE_FACTOR = 3;
    private static final int MINIMUM_CAPACITY = 4;
    private static final int MAXIMUM_CAPACITY = 1 << 29;

    private final int scaleFactor;
    private Object[] table;
    private int size;

    public SimpleIdentityHashSet() {
        this(MINIMUM_CAPACITY);
    }

    public SimpleIdentityHashSet(int expectedMaxSize) {
        this(expectedMaxSize, DEFAULT_SCALE_FACTOR);
    }

    public SimpleIdentityHashSet(int expectedMaxSize, int scaleFactor) {
        this.scaleFactor = Math.min(4, Math.max(1, scaleFactor));
        table = new Object[capacity(expectedMaxSize, this.scaleFactor)];
    }

    private static int capacity(int expectedMaxSize, int scaleFactor) {
        return (expectedMaxSize > MAXIMUM_CAPACITY / scaleFactor) ? MAXIMUM_CAPACITY :
              (expectedMaxSize <= MINIMUM_CAPACITY) ? MINIMUM_CAPACITY :
                    Integer.highestOneBit(expectedMaxSize * scaleFactor) << 1;
    }

    private static int hash(Object x, int length) {
        return System.identityHashCode(x) & (length - 1);
    }

    private static int nextIndex(int i, int len) {
        int next = i + 1;
        return (next < len ? next : 0);
    }

    public boolean add(Object o) {
        while (true) {
            final Object[] tab = table;
            final int len = tab.length;
            int i = hash(o, len);

            for (Object item; (item = tab[i]) != null; i = nextIndex(i, len)) {
                if (item == o) {
                    return false;
                }
            }

            final int s = size + 1;
            if (s*scaleFactor > len && resize(len)) continue;

            tab[i] = o;
            size = s;
            return true;
        }
    }

    public SimpleIdentityHashSet clear() {
        if (size > 0) {
            Arrays.fill(table, null);
            size = 0;
        }
        return this;
    }

    private boolean resize(int newCapacity) {
        int newLength = newCapacity << 1;

        Object[] oldTable = table;
        int oldLength = oldTable.length;
        if (oldLength == 2 * MAXIMUM_CAPACITY) { // can't expand any further
            if (size == MAXIMUM_CAPACITY - 1) {
                throw new IllegalStateException("Capacity exhausted.");
            }
            return false;
        }
        if (oldLength >= newLength)
            return false;

        Object[] newTable = new Object[newLength];

        for (Object o : oldTable) {
            if (o != null) {
                int i = hash(o, newLength);
                while (newTable[i] != null) {
                    i = nextIndex(i, newLength);
                }
                newTable[i] = o;
            }
        }
        table = newTable;
        return true;
    }

    public int length() {
        return table.length;
    }

    public int size() {
        return size;
    }

    public void ensureCapacity(int capacity) {
        int s = capacity + size();
        int len = s * scaleFactor;
        if ( len > length()) {
            resize(Integer.highestOneBit(len));
        }
    }
}
