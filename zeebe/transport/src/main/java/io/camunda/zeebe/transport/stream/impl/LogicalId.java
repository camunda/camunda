/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A logical id that identifies a stream. Multiple streams can have same logical id. A payload
 * generated for a stream should be accepted by another stream with same logical id.
 *
 * @param streamType type of the stream
 * @param metadata metadata of the stream
 * @param <M> type of metadata
 */
record LogicalId<M>(UnsafeBuffer streamType, M metadata) {

  @Override
  public String toString() {
    return "LogicalId{"
        + "streamType="
        + BufferUtil.bufferAsString(streamType)
        + ", metadata="
        + metadata
        + '}';
  }
}
