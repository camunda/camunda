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

/** Moves values between objects and bytes. */
final class Values {

  private Values() {}

  /** A private copy of {@code value} that later changes to {@code value} don't reach. */
  static DbValue copyOf(final DbValue value) {
    final var copy = value.newInstance();
    value.copyTo(copy);
    return copy;
  }

  /**
   * Copies {@code source} into {@code target}. Column families of the same name may use different
   * value classes (for example during migrations); between those, the copy goes through the encoded
   * form.
   */
  static void copy(final DbValue source, final DbValue target) {
    if (source.getClass() == target.getClass()) {
      source.copyTo(target);
    } else {
      final var bytes = encode(source, new ExpandableArrayBuffer());
      target.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    }
  }

  /** Decodes {@code bytes} into a new instance of the prototype's class. */
  static DbValue decode(final byte[] bytes, final DbValue prototype) {
    final var value = prototype.newInstance();
    value.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    return value;
  }

  /** Encodes {@code value}, using {@code buffer} as scratch space. */
  static byte[] encode(final DbValue value, final ExpandableArrayBuffer buffer) {
    final int length = value.write(buffer, 0);
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return bytes;
  }
}
