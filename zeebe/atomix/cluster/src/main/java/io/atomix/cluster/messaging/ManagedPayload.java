/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging;

import io.netty.buffer.ByteBuf;
import java.util.List;

public interface ManagedPayload {

  /**
   * Below this size, copying a payload into the (pooled) frame buffer is cheaper than the extra
   * buffer component and write vector a wrapped array costs; above it, wrapping avoids duplicating
   * the payload and keeps pooled frame buffers small.
   */
  int WRAP_THRESHOLD = 4 * 1024;

  int length();

  void encode(ByteBuf buffer, List<Object> out);

  /**
   * Materializes the payload as a byte array. Implementations that cannot (e.g. payloads streamed
   * from files) keep the default and must never be dispatched to byte-array consumers.
   */
  default byte[] toBytes() {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " cannot be materialized as a byte array");
  }

  default void release() {}
}
