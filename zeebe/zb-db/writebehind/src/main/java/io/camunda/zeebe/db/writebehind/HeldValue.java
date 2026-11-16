/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

/**
 * A value as the store holds it: its encoded form.
 *
 * <p>Nobody changes the bytes after creation, so the writer thread and any number of readers can
 * use a held value at the same time. The writer writes the bytes as they are and readers copy out
 * of them.
 */
final class HeldValue {

  private final byte[] bytes;

  /**
   * @param bytes exactly the encoded value, nothing more; the held value takes ownership
   */
  HeldValue(final byte[] bytes) {
    this.bytes = bytes;
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
    return "HeldValue{" + bytes.length + " bytes}";
  }
}
