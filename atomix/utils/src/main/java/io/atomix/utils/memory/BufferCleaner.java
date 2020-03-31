/*
 * Copyright 2019-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils.memory;

import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.filterReturnValue;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class which allows explicit calls to the DirectByteBuffer cleaner method instead of
 * relying on GC.
 */
public class BufferCleaner {

  private static final Logger LOGGER = LoggerFactory.getLogger(BufferCleaner.class);

  /** Reference to a Cleaner that does unmapping; no-op if not supported. */
  private static final Cleaner CLEANER;

  static {
    final Object hack =
        AccessController.doPrivileged((PrivilegedAction<Object>) BufferCleaner::unmapHackImpl);
    if (hack instanceof Cleaner) {
      CLEANER = (Cleaner) hack;
      LOGGER.debug("java.nio.DirectByteBuffer.cleaner(): available");
    } else {
      CLEANER =
          (ByteBuffer buffer) -> {
            // noop
          };
      LOGGER.debug("java.nio.DirectByteBuffer.cleaner(): unavailable {}", hack);
    }
  }

  private static Object unmapHackImpl() {
    final MethodHandles.Lookup lookup = lookup();
    try {
      try {
        // *** sun.misc.Unsafe unmapping (Java 9+) ***
        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        // first check if Unsafe has the right method, otherwise we can give up
        // without doing any security critical stuff:
        final MethodHandle unmapper =
            lookup.findVirtual(
                unsafeClass, "invokeCleaner", methodType(void.class, ByteBuffer.class));
        // fetch the unsafe instance and bind it to the virtual MH:
        final Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        final Object theUnsafe = f.get(null);
        return newBufferCleaner(ByteBuffer.class, unmapper.bindTo(theUnsafe));
      } catch (final SecurityException se) {
        // rethrow to report errors correctly (we need to catch it here, as we also catch
        // RuntimeException below!):
        throw se;
      } catch (final ReflectiveOperationException | RuntimeException e) {
        // *** sun.misc.Cleaner unmapping (Java 8) ***
        final Class<?> directBufferClass = Class.forName("java.nio.DirectByteBuffer");

        final Method m = directBufferClass.getMethod("cleaner");
        m.setAccessible(true);
        final MethodHandle directBufferCleanerMethod = lookup.unreflect(m);
        final Class<?> cleanerClass = directBufferCleanerMethod.type().returnType();

        /* "Compile" a MH that basically is equivalent to the following code:
         * void unmapper(ByteBuffer byteBuffer) {
         *   sun.misc.Cleaner cleaner = ((java.nio.DirectByteBuffer) byteBuffer).cleaner();
         *   if (Objects.nonNull(cleaner)) {
         *     cleaner.clean();
         *   } else {
         *     noop(cleaner); // the noop is needed because MethodHandles#guardWithTest always needs ELSE
         *   }
         * }
         */
        final MethodHandle cleanMethod =
            lookup.findVirtual(cleanerClass, "clean", methodType(void.class));
        final MethodHandle nonNullTest =
            lookup
                .findStatic(Objects.class, "nonNull", methodType(boolean.class, Object.class))
                .asType(methodType(boolean.class, cleanerClass));
        final MethodHandle noop =
            dropArguments(
                constant(Void.class, null).asType(methodType(void.class)), 0, cleanerClass);
        final MethodHandle unmapper =
            filterReturnValue(
                    directBufferCleanerMethod, guardWithTest(nonNullTest, cleanMethod, noop))
                .asType(methodType(void.class, ByteBuffer.class));
        return newBufferCleaner(directBufferClass, unmapper);
      }
    } catch (final SecurityException se) {
      return "Unmapping is not supported, because not all required permissions are given to the Lucene JAR file: "
          + se
          + " [Please grant at least the following permissions: RuntimePermission(\"accessClassInPackage.sun.misc\") "
          + " and ReflectPermission(\"suppressAccessChecks\")]";
    } catch (final ReflectiveOperationException | RuntimeException e) {
      return "Unmapping is not supported on this platform, because internal Java APIs are not compatible with this Atomix version: "
          + e;
    }
  }

  private static Cleaner newBufferCleaner(
      final Class<?> unmappableBufferClass, final MethodHandle unmapper) {
    return (ByteBuffer buffer) -> {
      if (!buffer.isDirect()) {
        return;
      }
      if (!unmappableBufferClass.isInstance(buffer)) {
        throw new IllegalArgumentException(
            "buffer is not an instance of " + unmappableBufferClass.getName());
      }
      final Throwable error =
          AccessController.doPrivileged(
              (PrivilegedAction<Throwable>)
                  () -> {
                    try {
                      unmapper.invokeExact(buffer);
                      return null;
                    } catch (final Throwable t) {
                      return t;
                    }
                  });
      if (error != null) {
        throw new IOException("Unable to unmap the mapped buffer", error);
      }
    };
  }

  /** Free {@link ByteBuffer} if possible. */
  public static void freeBuffer(final ByteBuffer buffer) throws IOException {
    CLEANER.freeBuffer(buffer);
  }
}
