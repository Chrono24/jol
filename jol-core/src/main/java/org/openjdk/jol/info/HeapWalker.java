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

import org.openjdk.jol.util.ObjectUtils;
import org.openjdk.jol.util.SimpleIdentityHashSet;
import org.openjdk.jol.util.SimpleStack;

import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.function.Function;


public class HeapWalker extends AbstractGraphWalker {

   private Visitor<Node>[] visitors = new Visitor[0];

   private TriPredicate<Object, Field, Object> isChildToBeTraversed = ( parent, field, child ) -> true;

   private SimpleIdentityHashSet visited;

   private ArraySizeCache  arraySizeCache;
   private ObjectSizeCache objectSizeCache;

   private int objectSizeCacheCapacity;
   private int identitySetCapacity;
   private int stackCapacity;

   public final <S extends Stats> S getStats( Function<Object[], S> statsFactory, Object... roots ) {

      verifyRoots(roots);

      S data = statsFactory.apply(roots);
      SimpleStack<Object> s = initializeContainers();

      for ( Object root : roots ) {
         if ( visited.add(root) ) {
            s.push(root);
         }
      }

      while ( !s.isEmpty() ) {
         Object o = s.pop();
         Class<?> cl = o.getClass();

         if ( cl.isArray() ) {
            data.addRecord(getCachedArraySize(o));

            if ( cl.getComponentType().isPrimitive() ) {
               // Nothing to do here
               continue;
            }

            Object[] arr = (Object[])o;

            if ( arr.length > 0 ) {
               int capacity = arr.length;
               s.ensureCapacity(capacity);
               visited.ensureCapacity(capacity);
            }

            for ( Object e : arr ) {
               if ( isElementTraversed(o, null, e) ) {
                  s.push(e);
               }
            }
         } else {
            data.addRecord(getCachedObjectSize(cl, o));

            for ( Field f : getAllReferenceFields(cl) ) {
               Object e = ObjectUtils.value(o, f);
               if ( isElementTraversed(o, f, e) ) {
                  s.push(e);
               }
            }
         }
      }

      data.setContainerCapacities(getStackCapacity(s), getIdentitySetCapacity(), getSizeCacheCapacity());
      return data;
   }

   public final <G extends Graph<N>, N extends Node> G getTree( Function<Object[], G> graphFactory,
         QuadFunction<N, String, Integer, Object, N> fieldNodeFactory, QuadFunction<N, Integer, Integer, Object, N> arrayNodeFactory, Consumer<N> nodeRecycler,
         Object... roots ) {

      verifyRoots(roots);

      G data = graphFactory.apply(roots);
      SimpleStack<N> s = initializeContainers();

      int rootId = 1;
      boolean single = (roots.length == 1);
      for ( Object root : roots ) {
         if ( visited.add(root) ) {
            String label = single ? "<root>" : ("<r" + rootId + ">");
            N node = fieldNodeFactory.apply(null, label, 0, root);
            s.push(node);
         }
         rootId++;
      }

      while ( !s.isEmpty() ) {
         N parent = s.pop();
         Object p = parent.obj();
         Class<?> cl = p.getClass();

         if ( cl.isArray() ) {
            parent.setSize(getCachedArraySize(p));

            if ( cl.getComponentType().isPrimitive() ) {
               // Nothing to do here
               visit(parent);
               data.addNode(parent);
               nodeRecycler.accept(parent);
               continue;
            }

            Object[] arr = (Object[])p;

            int used = 0;
            for ( int i = 0, n = arr.length; i < n; ++i ) {
               if ( arr[i] != null ) {
                  ++used;
               }
            }
            parent.setLength(arr.length);
            parent.setUsed(used);
            visit(parent);
            data.addNode(parent);

            if ( used > 0 ) {
               int capacity = used;
               s.ensureCapacity(capacity);
               visited.ensureCapacity(capacity);
            }

            for ( int i = 0; i < arr.length; i++ ) {
               Object c = arr[i];
               if ( isElementTraversed(p, null, c) ) {
                  N child = arrayNodeFactory.apply(parent, i, parent.depth() + 1, c);
                  s.push(child);
               }
            }
         } else {
            parent.setSize(getCachedObjectSize(cl, p));
            visit(parent);
            data.addNode(parent);

            for ( Field f : getAllReferenceFields(cl) ) {
               Object c = ObjectUtils.value(p, f);
               if ( isElementTraversed(p, f, c) ) {
                  N child = fieldNodeFactory.apply(parent, f.getName(), parent.depth() + 1, c);
                  s.push(child);
               }
            }
         }
         nodeRecycler.accept(parent);
      }

      data.setContainerCapacities(getStackCapacity(s), getIdentitySetCapacity(), getSizeCacheCapacity());
      return data;
   }

   public HeapWalker withConditionalRecursion( TriPredicate<Object, Field, Object> isChildToBeTraversed ) {
      this.isChildToBeTraversed = isChildToBeTraversed;
      return this;
   }

   public HeapWalker withIdentitySetCapacity( long capacity ) {
      if ( 0 < capacity && capacity <= Integer.MAX_VALUE ) {
         this.identitySetCapacity = (int)capacity;
      }
      return this;
   }

   public HeapWalker withArraySizeCache( ArraySizeCache cache ) {
      this.arraySizeCache = cache;
      return this;
   }

   public HeapWalker withObjectSizeCache( ObjectSizeCache cache ) {
      this.objectSizeCache = cache;
      return this;
   }

   public HeapWalker withObjectSizeCacheCapacity( int capacity ) {
      this.objectSizeCacheCapacity = capacity;
      return this;
   }

   public HeapWalker withStackCapacity( int capacity ) {
      this.stackCapacity = capacity;
      return this;
   }

   @SafeVarargs
   public final HeapWalker withVisitors( Visitor<Node>... visitors ) {
      this.visitors = visitors;
      return this;
   }

   private long getCachedArraySize( Object arr ) {
      return arraySizeCache.get(arr);
   }

   private long getCachedObjectSize( Class<?> cl, Object e ) {
      return objectSizeCache.get(cl, e);
   }

   private int getIdentitySetCapacity() {
      return visited.length();
   }

   private int getSizeCacheCapacity() {
      return objectSizeCache.size();
   }

   private <E> int getStackCapacity( SimpleStack<E> s ) {
      return s.length();
   }

   private <E> SimpleStack<E> initializeContainers() {
      if ( visited == null ) {
         visited = identitySetCapacity > 0 ? new SimpleIdentityHashSet(identitySetCapacity) : new SimpleIdentityHashSet();
      }
      if ( arraySizeCache == null ) {
         arraySizeCache = new ArraySizeCache.Passthrough();
      }
      if ( objectSizeCache == null ) {
         objectSizeCache = new ObjectSizeCache.WithHashMap(objectSizeCacheCapacity);
      }

      return stackCapacity > 0 ? new SimpleStack<>(stackCapacity) : new SimpleStack<>();
   }

   private boolean isElementTraversed( Object o, Field f, Object e ) {
      return e != null && isChildToBeTraversed.test(o, f, e) && visited.add(e);
   }

   private void visit( Node node ) {
      for ( Visitor<Node> v : visitors ) {
         v.visit(node);
      }
   }

   public interface Graph<N> extends Stats {

      void addNode( N node );
   }


   public interface Node {

      int depth();

      long getSize();

      Object obj();

      void setLength( long length );

      void setSize( long size );

      void setUsed( long used );
   }


   @FunctionalInterface
   public interface QuadFunction<T, U, V, W, R> {

      R apply( T t, U u, V v, W w );
   }


   public interface Stats {

      void addRecord( long size );

      void setContainerCapacities( int stackCapacity, int identitySetCapacity, int sizeCacheCapacity );
   }


   @FunctionalInterface
   public interface TriPredicate<T, U, V> {

      boolean test( T t, U u, V v );
   }


   public interface Visitor<N> {

      void visit( N node );
   }
}