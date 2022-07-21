/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.record;

import java.util.Objects;

public final class RecordMetadata {

  private final long checksum;
  private final int length;

  public RecordMetadata(final long checksum, final int length) {
    this.checksum = checksum;
    this.length = length;
  }

  public long checksum() {
    return checksum;
  }

  public int length() {
    return length;
  }

  @Override
  public int hashCode() {
    return Objects.hash(checksum, length);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (RecordMetadata) obj;
    return checksum == that.checksum && length == that.length;
  }

  @Override
  public String toString() {
    return "RecordMetadata[" + "checksum=" + checksum + ", " + "length=" + length + ']';
  }
}
