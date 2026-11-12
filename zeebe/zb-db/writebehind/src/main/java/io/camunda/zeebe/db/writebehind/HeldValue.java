/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbValue;

/**
 * A value as the store holds it: its encoded form and an object of the caller's value class that
 * wraps exactly these bytes.
 *
 * <p>Nobody changes either after creation, so the writer thread and any number of readers can use a
 * held value at the same time. The writer writes the bytes as they are and readers copy out of
 * them; nobody calls {@link DbValue#write} on the object, as an encoder may keep shared scratch
 * state. The object stays here so that a faster copy out of it can replace the copy from the bytes
 * later.
 */
final class HeldValue {

  private final DbValue object;
  private final byte[] bytes;

  /**
   * @param object wraps {@code bytes}; the held value takes ownership of both
   * @param bytes exactly the encoded value, nothing more
   */
  HeldValue(final DbValue object, final byte[] bytes) {
    this.object = object;
    this.bytes = bytes;
  }

  /** The decoded value. Shared: callers must not change it, nor hand it out. */
  DbValue object() {
    return object;
  }

  /** The encoded value. Shared: callers must not change it. */
  byte[] bytes() {
    return bytes;
  }

  int length() {
    return bytes.length;
  }

  @Override
  public String toString() {
    return "HeldValue{" + object + ", " + bytes.length + " bytes}";
  }
}
