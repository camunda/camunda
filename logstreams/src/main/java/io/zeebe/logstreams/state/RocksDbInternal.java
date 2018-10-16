/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.state;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;

public class RocksDbInternal {

  static Field columnFamilyHandle;
  static Field rocksDbNativeHandle;

  static Method putMethod;
  static Method putWithHandle;

  static Method getWithHandle;
  static Method getMethod;

  static Method removeMethod;
  static Method removeWithHandle;

  static {
    RocksDB.loadLibrary();

    try {
      resolveInternalMethods();
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static void resolveInternalMethods() throws NoSuchFieldException, NoSuchMethodException {
    nativeHandles();

    putMethod();
    putWithHandle();

    getMethod();
    getWithHandle();

    removeMethod();
    removeWithHandle();
  }

  private static void nativeHandles() throws NoSuchFieldException {
    columnFamilyHandle = ColumnFamilyHandle.class.getSuperclass().getDeclaredField("nativeHandle_");
    columnFamilyHandle.setAccessible(true);

    rocksDbNativeHandle = RocksDB.class.getSuperclass().getDeclaredField("nativeHandle_");
    rocksDbNativeHandle.setAccessible(true);
  }

  private static void putMethod() throws NoSuchMethodException {
    putMethod =
        RocksDB.class.getDeclaredMethod(
            "put",
            Long.TYPE,
            byte[].class,
            Integer.TYPE,
            Integer.TYPE,
            byte[].class,
            Integer.TYPE,
            Integer.TYPE);
    putMethod.setAccessible(true);
  }

  private static void putWithHandle() throws NoSuchMethodException {
    putWithHandle =
        RocksDB.class.getDeclaredMethod(
            "put",
            Long.TYPE,
            byte[].class,
            Integer.TYPE,
            Integer.TYPE,
            byte[].class,
            Integer.TYPE,
            Integer.TYPE,
            Long.TYPE);
    putWithHandle.setAccessible(true);
  }

  private static void getMethod() throws NoSuchMethodException {
    getMethod =
        RocksDB.class.getDeclaredMethod(
            "get",
            Long.TYPE,
            byte[].class,
            Integer.TYPE,
            Integer.TYPE,
            byte[].class,
            Integer.TYPE,
            Integer.TYPE);
    getMethod.setAccessible(true);
  }

  private static void getWithHandle() throws NoSuchMethodException {
    getWithHandle =
        RocksDB.class.getDeclaredMethod(
            "get",
            Long.TYPE,
            byte[].class,
            Integer.TYPE,
            Integer.TYPE,
            byte[].class,
            Integer.TYPE,
            Integer.TYPE,
            Long.TYPE);
    getWithHandle.setAccessible(true);
  }

  private static void removeMethod() throws NoSuchMethodException {
    removeMethod =
        RocksDB.class.getDeclaredMethod(
            "delete", Long.TYPE, byte[].class, Integer.TYPE, Integer.TYPE);
    removeMethod.setAccessible(true);
  }

  private static void removeWithHandle() throws NoSuchMethodException {
    removeWithHandle =
        RocksDB.class.getDeclaredMethod(
            "delete", Long.TYPE, byte[].class, Integer.TYPE, Integer.TYPE, Long.TYPE);
    removeWithHandle.setAccessible(true);
  }
}
