/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class PublishBatchRequest implements BufferReader, BufferWriter {

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {}

  @Override
  public int getLength() {
    return 0;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return 0;
  }
}
