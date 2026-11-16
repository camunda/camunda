/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbValue;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Moves values between the callers' objects and the {@link HeldValue}s of the store.
 *
 * <p>A held value's bytes never change, so a copy out of them can wrap them directly: a value class
 * may keep views of the buffer it wraps, and these views stay valid. A copy never wraps a scratch
 * buffer, which the next encode would overwrite.
 */
final class Values {

  private Values() {}

  /**
   * A held copy of {@code value}, which later changes to {@code value} don't reach. Encodes it
   * once, using {@code scratch} as scratch space, into an array of the exact length.
   */
  static HeldValue hold(final DbValue value, final ExpandableArrayBuffer scratch) {
    final int length = value.write(scratch, 0);
    final var bytes = new byte[length];
    scratch.getBytes(0, bytes);
    return new HeldValue(bytes);
  }

  /**
   * Copies {@code held} into {@code target}, which may be of another value class than the held
   * object: column families of the same name may use different value classes, for example during
   * migrations.
   */
  static void copyInto(final HeldValue held, final DbValue target) {
    final var bytes = held.bytes();
    target.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
  }
}
