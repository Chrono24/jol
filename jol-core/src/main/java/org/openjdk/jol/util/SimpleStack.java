/*
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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
 * Stack implementation optimized for JOL uses. Cuts corners where it can.
 * @param <E>
 */
public class SimpleStack<E> {
    private static final int MINIMUM_CAPACITY = 2;

    Object[] elements;
    int head;

    public SimpleStack() {
        this(MINIMUM_CAPACITY);
    }

    public SimpleStack(int expectedMaxSize) {
        head = -1;
        elements = new Object[capacity(Math.max(MINIMUM_CAPACITY, expectedMaxSize))];
    }

    private static int capacity( int expectedMaxSize ) {
        int candidate = Integer.highestOneBit(expectedMaxSize);
        return candidate >= expectedMaxSize ? candidate : candidate<<1;
    }

    private void resize() {
        elements = Arrays.copyOf(elements, elements.length * 2);
    }

    public boolean isEmpty() {
        return head == -1;
    }

    public void push(E e) {
        head++;
        if (head == elements.length) {
            resize();
        }
        elements[head] = e;
    }

    public E pop() {
        Object e = elements[head];
        elements[head] = null;
        head--;
        return (E) e;
    }

    public int length() {
        return elements.length;
    }

    public int size() {
        return head + 1;
    }


    public SimpleStack<E> clear() {
        if (head > -1) {
            Arrays.fill(elements, 0, head, null);
            head = -1;
        }
        return this;
    }

    public void ensureCapacity(int capacity) {
        int target = capacity + size();
        if (target > length()) {
            resize(capacity(target));
        }
    }

    private void resize(int capacity) {
        elements = Arrays.copyOf(elements, capacity);
    }
}
