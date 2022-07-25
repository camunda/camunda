/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.record;

import java.util.Objects;
import org.agrona.DirectBuffer;

public final class RecordData {

  private final long index;
  private final long asqn;
  private final DirectBuffer data;

  public RecordData(final long index, final long asqn, final DirectBuffer data) {
    this.index = index;
    this.asqn = asqn;
    this.data = data;
  }

  public long index() {
    return index;
  }

  public long asqn() {
    return asqn;
  }

  public DirectBuffer data() {
    return data;
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, asqn, data);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (RecordData) obj;
    return index == that.index && asqn == that.asqn && Objects.equals(data, that.data);
  }

  @Override
  public String toString() {
    return "RecordData[" + "index=" + index + ", " + "asqn=" + asqn + ", " + "data=" + data + ']';
  }
}
