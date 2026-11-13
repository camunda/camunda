/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

/** The encoded key/value entries of properties a {@link MsgpackPojo} does not declare. */
public final class UnknownProperties {
  private final ExpandableArrayBuffer entries = new ExpandableArrayBuffer(0);
  private int count;
  private int length;

  /** Appends one already encoded key/value entry. The bytes are copied. */
  public void append(final DirectBuffer source, final int offset, final int entryLength) {
    entries.putBytes(length, source, offset, entryLength);
    length += entryLength;
    count++;
  }

  /** Replaces the entries by a copy of the ones of {@code other}. */
  public void copyFrom(final UnknownProperties other) {
    if (other == this) {
      return;
    }
    clear();
    if (other.length > 0) {
      entries.putBytes(0, other.entries, 0, other.length);
    }
    length = other.length;
    count = other.count;
  }

  public int count() {
    return count;
  }

  /** The number of bytes of the encoded entries. */
  public int length() {
    return length;
  }

  public DirectBuffer buffer() {
    return entries;
  }

  public void clear() {
    count = 0;
    length = 0;
  }

  public boolean isEmpty() {
    return count == 0;
  }
}
