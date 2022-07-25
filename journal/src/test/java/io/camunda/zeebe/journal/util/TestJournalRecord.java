/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.util;

import io.camunda.zeebe.journal.JournalRecord;
import java.util.Objects;
import org.agrona.DirectBuffer;

public final class TestJournalRecord implements JournalRecord {

  private final long index;
  private final long asqn;
  private final long checksum;
  private final DirectBuffer data;

  public TestJournalRecord(
      final long index, final long asqn, final long checksum, final DirectBuffer data) {
    this.index = index;
    this.asqn = asqn;
    this.checksum = checksum;
    this.data = data;
  }

  @Override
  public long index() {
    return index;
  }

  @Override
  public long asqn() {
    return asqn;
  }

  @Override
  public long checksum() {
    return checksum;
  }

  @Override
  public DirectBuffer data() {
    return data;
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, asqn, checksum, data);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (TestJournalRecord) obj;
    return index == that.index
        && asqn == that.asqn
        && checksum == that.checksum
        && Objects.equals(data, that.data);
  }

  @Override
  public String toString() {
    return "TestJournalRecord["
        + "index="
        + index
        + ", "
        + "asqn="
        + asqn
        + ", "
        + "checksum="
        + checksum
        + ", "
        + "data="
        + data
        + ']';
  }
}
