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
package org.openjdk.jol.info;

import org.openjdk.jol.util.SimpleIdentityHashSet;

public interface VisitedIdentities {

    void ensureCapacity(int capacity);

    boolean add(Object o);

    int size();

    VisitedIdentities clear();

    class WithSimpleIdentityHashSet implements VisitedIdentities {

        private final SimpleIdentityHashSet set;

        public WithSimpleIdentityHashSet(int initialCapacity) {
            set = new SimpleIdentityHashSet(initialCapacity);
        }

        public WithSimpleIdentityHashSet(int initialCapacity, int scaleFactor) {
            set = new SimpleIdentityHashSet(initialCapacity, scaleFactor);
        }

        @Override
        public boolean add(Object o) {
            return set.add(o);
        }

        @Override
        public int size() {
            return set.size();
        }

        @Override
        public VisitedIdentities clear() {
            set.clear();
            return this;
        }

        @Override
        public void ensureCapacity(int capacity) {
            set.ensureCapacity(capacity);
        }
    }

    class WithSegmentedSimpleIdentityHashSet implements VisitedIdentities {

        private static int hash(Object x, int rightShift ) {
            return System.identityHashCode(x) >>> rightShift;
        }

        private final SimpleIdentityHashSet[] segments;
        private final int rightShift;

        public WithSegmentedSimpleIdentityHashSet(int segmentCount, int initialCapacity) {
            segments = new SimpleIdentityHashSet[Integer.highestOneBit(segmentCount)];
            rightShift = Integer.numberOfLeadingZeros(segments.length) + 1;

            int perSegmentInitialCapacity = initialCapacity / segments.length + 1;
            for ( int i = 0; i < segments.length; i++ ) {
                segments[i] = new SimpleIdentityHashSet(perSegmentInitialCapacity);
            }
        }

        public WithSegmentedSimpleIdentityHashSet(int segmentCount, int initialCapacity, int scaleFactor) {
            segments = new SimpleIdentityHashSet[Integer.highestOneBit(segmentCount)];
            rightShift = Integer.numberOfLeadingZeros(segments.length) + 1;

            int perSegmentInitialCapacity = initialCapacity / segments.length + 1;
            for ( int i = 0; i < segments.length; i++ ) {
                segments[i] = new SimpleIdentityHashSet(perSegmentInitialCapacity, scaleFactor);
            }
        }

        @Override
        public boolean add(Object o) {
            int index = hash(o, rightShift);
            return segments[index].add(o);
        }

        @Override
        public int size() {
            int size = 0;
            for ( SimpleIdentityHashSet segment : segments ) {
                size += segment.size();
            }
            return size;
        }

        @Override
        public VisitedIdentities clear() {
            for ( SimpleIdentityHashSet segment : segments ) {
                segment.clear();
            }
            return this;
        }

        @Override
        public void ensureCapacity(int capacity) {
            int perSegmentCapacity = capacity / segments.length + 1;
            for ( SimpleIdentityHashSet segment : segments ) {
                segment.ensureCapacity(perSegmentCapacity);
            }
        }
    }
}