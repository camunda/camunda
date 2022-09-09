/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.record;

import io.camunda.zeebe.journal.RecordDataWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class DirectCopyRecordDataWriter implements RecordDataWriter {
  private final DirectBuffer source;

  public DirectCopyRecordDataWriter(final DirectBuffer source) {
    this.source = source;
  }

  @Override
  public int getRecordLength() {
    return source.capacity();
  }

  @Override
  public int write(final MutableDirectBuffer writeBuffer, final int offset) {
    writeBuffer.putBytes(offset, source, 0, source.capacity());
    return source.capacity();
  }
}
