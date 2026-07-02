/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import java.nio.ByteBuffer;

/** Byte codec for a {@code Long} accumulator (8 bytes), e.g. for count/sum aggregates. */
public final class LongCodec implements Codec<Long> {

  @Override
  public byte[] encode(final Long value) {
    return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
  }

  @Override
  public Long decode(final byte[] bytes) {
    return ByteBuffer.wrap(bytes).getLong();
  }
}
