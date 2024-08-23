/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jol.util.SimpleStack;

import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.function.Function;


public class HeapWalker extends AbstractGraphWalker {

    private Visitor<Node>[] visitors = new Visitor[0];

    private TriPredicate<Object, Field, Object> isChildToBeTraversed = (parent, field, child) -> true;

    private VisitedIdentities visited;

    private ArraySizeCache arraySizeCache;
    private ObjectSizeCache objectSizeCache;
    private ReferenceFieldCache referenceFieldCache;
    private SimpleStack<?> stack;

    private int objectSizeCacheCapacity;
    private int identitySetCapacity;
    private int stackCapacity;

    public final <S extends Stats> S getStats(Function<Object[], S> statsFactory, Object... roots) {

        verifyRoots(roots);

        S data = statsFactory.apply(roots);
        //noinspection unchecked
        SimpleStack<Object> s = (SimpleStack<Object>)initializeContainers();

        for (Object root : roots) {
            if (visited.add(root)) {
                s.push(root);
            }
        }

        while (!s.isEmpty()) {
            Object o = s.pop();
            Class<?> cl = o.getClass();

            if (cl.isArray()) {
                data.addRecord(getCachedArraySize(o));

                if (cl.getComponentType().isPrimitive()) {
                    // Nothing to do here
                    continue;
                }

                Object[] arr = (Object[]) o;

                if (arr.length > 0) {
                    int capacity = arr.length;
                    s.ensureCapacity(capacity);
                    visited.ensureCapacity(capacity);
                }

                for (Object e : arr) {
                    if (isElementTraversed(o, null, e)) {
                        s.push(e);
                    }
                }
            } else {
                data.addRecord(getCachedObjectSize(cl, o));

                for (Field f : getAllReferenceFields(cl)) {
                    Object e = getCachedReferenceField(f, o);
                    if (isElementTraversed(o, f, e)) {
                        s.push(e);
                    }
                }
            }
        }

        data.setContainerCapacities(getStackCapacity(), getIdentitySetCapacity(), getSizeCacheCapacity());
        return data;
    }

    public final <G extends Graph<N>, N extends Node> G getTree(Function<Object[], G> graphFactory,
                                                                FieldNodeFactory<N> fieldNodeFactory, ArrayNodeFactory<N> arrayNodeFactory, Consumer<N> nodeRecycler,
                                                                Object... roots) {

        verifyRoots(roots);

        G data = graphFactory.apply(roots);
        //noinspection unchecked
        SimpleStack<N> s = (SimpleStack<N>)initializeContainers();

        int rootId = 1;
        boolean single = (roots.length == 1);
        for (Object root : roots) {
            if (visited.add(root)) {
                String label = single ? "<root>" : ("<r" + rootId + ">");
                N node = fieldNodeFactory.apply(null, label, 0, root);
                s.push(node);
            }
            rootId++;
        }

        while (!s.isEmpty()) {
            N parent = s.pop();
            Object p = parent.obj();
            Class<?> cl = p.getClass();

            if (cl.isArray()) {
                parent.setSize(getCachedArraySize(p));

                if (cl.getComponentType().isPrimitive()) {
                    // Nothing to do here
                    addPrimitiveArrayInfo(parent, p, cl);
                    visit(parent);
                    data.addNode(parent);
                    nodeRecycler.accept(parent);
                    continue;
                }

                Object[] arr = (Object[]) p;

                int used = 0;
                for (int i = 0, n = arr.length; i < n; ++i) {
                    if (arr[i] != null) {
                        ++used;
                    }
                }
                parent.setLength(arr.length);
                parent.setUsed(used);
                visit(parent);
                data.addNode(parent);

                if (used > 0) {
                    int capacity = used;
                    s.ensureCapacity(capacity);
                    visited.ensureCapacity(capacity);
                }

                for (int i = 0; i < arr.length; i++) {
                    Object c = arr[i];
                    if (isElementTraversed(p, null, c)) {
                        N child = arrayNodeFactory.apply(parent, i, parent.depth() + 1, c);
                        s.push(child);
                    }
                }
            } else {
                parent.setSize(getCachedObjectSize(cl, p));
                visit(parent);
                data.addNode(parent);

                for (Field f : getAllReferenceFields(cl)) {
                    Object c = getCachedReferenceField(f, p);
                    if (isElementTraversed(p, f, c)) {
                        N child = fieldNodeFactory.apply(parent, f.getName(), parent.depth() + 1, c);
                        s.push(child);
                    }
                }
            }
            nodeRecycler.accept(parent);
        }

        data.setContainerCapacities(getStackCapacity(), getIdentitySetCapacity(), getSizeCacheCapacity());
        return data;
    }

    public HeapWalker withConditionalRecursion(TriPredicate<Object, Field, Object> isChildToBeTraversed) {
        this.isChildToBeTraversed = isChildToBeTraversed;
        return this;
    }

    public HeapWalker withIdentitySet(VisitedIdentities visited) {
        this.visited = visited;
        return this;
    }

    public HeapWalker withIdentitySetCapacity(long capacity) {
        if (0 < capacity && capacity <= Integer.MAX_VALUE) {
            this.identitySetCapacity = (int) capacity;
        }
        return this;
    }

    public HeapWalker withArraySizeCache(ArraySizeCache cache) {
        this.arraySizeCache = cache;
        return this;
    }

    public HeapWalker withObjectSizeCache(ObjectSizeCache cache) {
        this.objectSizeCache = cache;
        return this;
    }

    public HeapWalker withObjectSizeCacheCapacity(int capacity) {
        this.objectSizeCacheCapacity = capacity;
        return this;
    }

    public HeapWalker withReferenceFieldCache(ReferenceFieldCache cache) {
        this.referenceFieldCache = cache;
        return this;
    }

    public HeapWalker withStack(SimpleStack<Object> stack) {
        this.stack = stack;
        return this;
    }

    public HeapWalker withStackCapacity(int capacity) {
        this.stackCapacity = capacity;
        return this;
    }

    @SafeVarargs
    public final HeapWalker withVisitors(Visitor<Node>... visitors) {
        this.visitors = visitors;
        return this;
    }

    private <N extends Node> void addPrimitiveArrayInfo(N node, Object o, Class<?> cl) {
        long length = 0, used = 0;

        if (cl == byte[].class) {
            byte[] arr = (byte[]) o;
            length = arr.length;
            for (int i = 0; i < length; ++i) {
                if (arr[i] != 0) {
                    ++used;
                }
            }
        } else if (cl == boolean[].class) {
            return; // kind of nonsensical
        } else if (cl == short[].class) {
            short[] arr = (short[]) o;
            length = arr.length;
            for (int i = 0; i < length; ++i) {
                if (arr[i] != 0) {
                    ++used;
                }
            }
        } else if (cl == char[].class) {
            char[] arr = (char[]) o;
            length = arr.length;
            for (int i = 0; i < length; ++i) {
                if (arr[i] != 0) {
                    ++used;
                }
            }
        } else if (cl == int[].class) {
            int[] arr = (int[]) o;
            length = arr.length;
            for (int i = 0; i < length; ++i) {
                if (arr[i] != 0) {
                    ++used;
                }
            }
        } else if (cl == float[].class) {
            float[] arr = (float[]) o;
            length = arr.length;
            for (int i = 0; i < length; ++i) {
                if (arr[i] != 0.0f) {
                    ++used;
                }
            }
        } else if (cl == double[].class) {
            double[] arr = (double[]) o;
            length = arr.length;
            for (int i = 0; i < length; ++i) {
                if (arr[i] != 0.0) {
                    ++used;
                }
            }
        } else if (cl == long[].class) {
            long[] arr = (long[]) o;
            length = arr.length;
            for (int i = 0; i < length; ++i) {
                if (arr[i] != 0L) {
                    ++used;
                }
            }
        }
        if (length > 0) {
            node.setLength(length);
            node.setUsed(used);
        }
    }

    private long getCachedArraySize(Object arr) {
        return arraySizeCache.get(arr);
    }

    private long getCachedObjectSize(Class<?> cl, Object e) {
        return objectSizeCache.get(cl, e);
    }

    private Object getCachedReferenceField( Field f, Object e) {
        return referenceFieldCache.get(f, e);
    }

    private int getIdentitySetCapacity() {
        return visited.size();
    }

    private int getSizeCacheCapacity() {
        return objectSizeCache.size();
    }

    private int getStackCapacity() {
        return stack.length();
    }

    private SimpleStack<?> initializeContainers() {
        if (visited == null) {
            visited = new VisitedIdentities.WithSimpleIdentityHashSet(identitySetCapacity);
        }
        if (arraySizeCache == null) {
            arraySizeCache = new ArraySizeCache.Passthrough();
        }
        if (objectSizeCache == null) {
            objectSizeCache = new ObjectSizeCache.WithHashMap(objectSizeCacheCapacity);
        }
        if (referenceFieldCache == null) {
            referenceFieldCache = new ReferenceFieldCache.WithHashMap(objectSizeCacheCapacity);
        }

        if (stack == null ) {
            stack = stackCapacity > 0 ? new SimpleStack<>(stackCapacity) : new SimpleStack<>();
        } else {
            stack.clear();
        }

        return stack;
    }

    private boolean isElementTraversed(Object parent, Field field, Object child) {
        return child != null && isChildToBeTraversed.test(parent, field, child) && visited.add(child);
    }

    private void visit(Node node) {
        for (Visitor<Node> v : visitors) {
            v.visit(node);
        }
    }


    public interface Graph<N> extends Stats {

        void addNode(N node);
    }


    public interface Node {

        int depth();

        void setSize(long size);

        Object obj();

        void setLength(long length);

        void setUsed(long used);
    }


    @FunctionalInterface
    public interface FieldNodeFactory<Node> {

        Node apply(Node parentNode, String fieldName, int depth, Object childObject);
    }


    @FunctionalInterface
    public interface ArrayNodeFactory<Node> {

        Node apply(Node parentNode, int index, int depth, Object childObject);
    }


    public interface Stats {

        void addRecord(long size);

        void setContainerCapacities(int stackCapacity, int identitySetCapacity, int sizeCacheCapacity);
    }


    @FunctionalInterface
    public interface TriPredicate<T, U, V> {

        boolean test(T t, U u, V v);
    }


    public interface Visitor<N> {

        void visit(N node);
    }
}