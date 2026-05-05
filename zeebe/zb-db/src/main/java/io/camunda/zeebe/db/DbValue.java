/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import io.camunda.zeebe.util.Copyable;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.lang.reflect.Method;

/**
 * The value which should be stored together with a key.
 *
 * <p>Extends {@link Copyable} for zero-serialization in-memory storage. The defaults fall back to
 * serialization via {@link BufferWriter}/{@link BufferReader}. Concrete types should override both
 * {@link #copyTo} and {@link #newInstance} for maximum performance.
 */
public interface DbValue extends BufferWriter, BufferReader, Copyable<DbValue> {

  ClassValue<ReflectionBridge> REFLECTION_BRIDGES =
      new ClassValue<>() {
        @Override
        protected ReflectionBridge computeValue(final Class<?> type) {
          Method copyToMethod = null;
          Method createNewInstanceMethod = null;

          for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            final Method[] declaredMethods;
            try {
              declaredMethods = current.getDeclaredMethods();
            } catch (final LinkageError | TypeNotPresentException ignored) {
              // Some DbValue implementations may declare methods whose signatures reference types
              // that are not available on the current runtime classpath. In that case, skip
              // reflective bridge discovery for the affected class and continue with its
              // superclass. We can still fall back to serialization or constructor-based
              // instantiation.
              continue;
            }

            for (final Method method : declaredMethods) {
              if (copyToMethod == null
                  && method.getName().equals("copyTo")
                  && method.getParameterCount() == 1
                  && !method.getParameterTypes()[0].equals(DbValue.class)) {
                method.setAccessible(true);
                copyToMethod = method;
              }
              if (createNewInstanceMethod == null
                  && method.getName().equals("createNewInstance")
                  && method.getParameterCount() == 0) {
                method.setAccessible(true);
                createNewInstanceMethod = method;
              }
            }
          }

          return new ReflectionBridge(copyToMethod, createNewInstanceMethod);
        }
      };

  record ReflectionBridge(Method copyToMethod, Method createNewInstanceMethod) {}

  /**
   * {@inheritDoc}
   *
   * <p>Default: serializes {@code this} to a temporary buffer and wraps {@code target} from it.
   */
  @Override
  default void copyTo(final DbValue target) {
    final var bridge = REFLECTION_BRIDGES.get(getClass());
    final var copyToMethod = bridge.copyToMethod();
    if (copyToMethod != null && copyToMethod.getParameterTypes()[0].isInstance(target)) {
      try {
        copyToMethod.invoke(this, target);
        return;
      } catch (final ReflectiveOperationException e) {
        throw new RuntimeException("Failed to invoke reflective copyTo bridge", e);
      }
    }

    final int length = getLength();
    final byte[] bytes = new byte[length];
    final org.agrona.MutableDirectBuffer buf = new org.agrona.concurrent.UnsafeBuffer(bytes);
    write(buf, 0);
    target.wrap(buf, 0, length);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Default: uses reflection to find a no-arg constructor.
   */
  @Override
  default DbValue newInstance() {
    final var bridge = REFLECTION_BRIDGES.get(getClass());
    final var createNewInstanceMethod = bridge.createNewInstanceMethod();
    if (createNewInstanceMethod != null) {
      try {
        return (DbValue) createNewInstanceMethod.invoke(this);
      } catch (final ReflectiveOperationException e) {
        throw new RuntimeException("Failed to invoke reflective createNewInstance bridge", e);
      }
    }

    try {
      final var ctor = getClass().getDeclaredConstructor();
      ctor.setAccessible(true);
      return (DbValue) ctor.newInstance();
    } catch (final NoSuchMethodException e) {
      throw new UnsupportedOperationException(
          getClass().getName() + " has no no-arg constructor. Override newInstance() manually.", e);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to create new instance of " + getClass().getName(), e);
    }
  }
}
