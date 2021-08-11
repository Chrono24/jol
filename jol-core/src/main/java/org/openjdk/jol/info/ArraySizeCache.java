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

import org.openjdk.jol.vm.VM;
import org.openjdk.jol.vm.VirtualMachine;


public interface ArraySizeCache {

   long get( Object arr );

   class Passthrough implements ArraySizeCache {

      private final VirtualMachine vm = VM.current();

      @Override
      public long get( Object arr ) {
         return vm.sizeOf(arr);
      }
   }


   class Precalculated implements ArraySizeCache {

      private static final int ARRAY_HEADER_SIZE;
      private static final int MASK;
      private static final int OBJECT_ALIGNMENT;

      private static final int BYTE_SIZE;
      private static final int BOOLEAN_SIZE;
      private static final int SHORT_SIZE;
      private static final int CHAR_SIZE;
      private static final int INT_SIZE;
      private static final int FLOAT_SIZE;
      private static final int DOUBLE_SIZE;
      private static final int LONG_SIZE;
      private static final int OBJECT_SIZE;

      static {
         VirtualMachine vm = VM.current();
         int shift = 31 - Integer.numberOfLeadingZeros(vm.objectAlignment());

         ARRAY_HEADER_SIZE = vm.arrayHeaderSize();
         MASK = (0xffffffff >> shift) << shift;
         OBJECT_ALIGNMENT = vm.objectAlignment();

         BYTE_SIZE = vm.arrayIndexScale("byte");
         BOOLEAN_SIZE = vm.arrayIndexScale("boolean");
         SHORT_SIZE = vm.arrayIndexScale("short");
         CHAR_SIZE = vm.arrayIndexScale("char");
         INT_SIZE = vm.arrayIndexScale("int");
         FLOAT_SIZE = vm.arrayIndexScale("float");
         DOUBLE_SIZE = vm.arrayIndexScale("double");
         LONG_SIZE = vm.arrayIndexScale("long");
         OBJECT_SIZE = vm.arrayIndexScale("Object[]");
      }

      private static int arraySize( int elementSize, int numElements ) {
         int usedSize = ARRAY_HEADER_SIZE + elementSize * numElements;
         int floor = usedSize & MASK;
         int ceil = floor + OBJECT_ALIGNMENT;
         return usedSize > floor ? ceil : floor;
      }

      public long get( Object arr ) {
         Class<?> cl = arr.getClass();
         if ( cl == byte[].class ) {
            return arraySize(BYTE_SIZE, ((byte[])arr).length);
         }
         if ( cl == boolean[].class ) {
            return arraySize(BOOLEAN_SIZE, ((boolean[])arr).length);
         }
         if ( cl == short[].class ) {
            return arraySize(SHORT_SIZE, ((short[])arr).length);
         }
         if ( cl == char[].class ) {
            return arraySize(CHAR_SIZE, ((char[])arr).length);
         }
         if ( cl == int[].class ) {
            return arraySize(INT_SIZE, ((int[])arr).length);
         }
         if ( cl == float[].class ) {
            return arraySize(FLOAT_SIZE, ((float[])arr).length);
         }
         if ( cl == double[].class ) {
            return arraySize(DOUBLE_SIZE, ((double[])arr).length);
         }
         if ( cl == long[].class ) {
            return arraySize(LONG_SIZE, ((long[])arr).length);
         }
         return arraySize(OBJECT_SIZE, ((Object[])arr).length);
      }
   }
}
