/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.stream;

import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.MutableDirectBuffer;

public class ProcessInstanceProperties implements BufferWriter {

  private final long processInstanceKey;

  public ProcessInstanceProperties(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public int getLength() {
    return Long.BYTES;
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    buffer.putLong(offset, processInstanceKey);
  }
}
