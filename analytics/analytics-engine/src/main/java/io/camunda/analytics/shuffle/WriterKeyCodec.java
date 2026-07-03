/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.shuffle;

import io.camunda.eventbridge.streaming.aggregate.Codec;
import java.nio.ByteBuffer;

/**
 * Codec for a {@link WriterKey}: the inner key's bytes followed by the writer id. Lets the Stage-1
 * combiner persist its per-writer cells with an existing key codec, unchanged.
 *
 * @param <K> the base grouping key type
 */
public final class WriterKeyCodec<K> implements Codec<WriterKey<K>> {

  private final Codec<K> keyCodec;

  public WriterKeyCodec(final Codec<K> keyCodec) {
    this.keyCodec = keyCodec;
  }

  @Override
  public byte[] encode(final WriterKey<K> value) {
    final byte[] keyBytes = keyCodec.encode(value.key());
    return ByteBuffer.allocate(keyBytes.length + Integer.BYTES)
        .put(keyBytes)
        .putInt(value.writer())
        .array();
  }

  @Override
  public WriterKey<K> decode(final byte[] bytes) {
    final int writer = ByteBuffer.wrap(bytes, bytes.length - Integer.BYTES, Integer.BYTES).getInt();
    final byte[] keyBytes = new byte[bytes.length - Integer.BYTES];
    System.arraycopy(bytes, 0, keyBytes, 0, keyBytes.length);
    return new WriterKey<>(keyCodec.decode(keyBytes), writer);
  }
}
