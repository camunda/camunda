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

/**
 * The value which should be stored together with a key.
 *
 * <p>Extends {@link Copyable} for zero-serialization in-memory storage. The defaults fall back to
 * serialization via {@link BufferWriter}/{@link BufferReader}. Concrete types should override both
 * {@link #copyTo} and {@link #newInstance} for maximum performance.
 */
public interface DbValue extends BufferWriter, BufferReader, Copyable<DbValue> {

  /**
   * {@inheritDoc}
   *
   * <p>Default: serializes {@code this} to a temporary buffer and wraps {@code target} from it.
   */
  @Override
  default void copyTo(final DbValue target) {
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
