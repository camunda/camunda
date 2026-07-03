/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.shuffle;

import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Record flyweight for a {@link WriterKey}: the inner key's own serialized bytes plus the writer
 * id. Lets the Stage-1 combiner persist its per-writer cells by composing whatever record flyweight
 * the metric already supplies for its base key.
 *
 * @param <K> the base grouping key type
 */
public final class WriterKeyValue<K> extends UnpackedObject implements RecordValue<WriterKey<K>> {

  private final BinaryProperty keyProp = new BinaryProperty("key");
  private final IntegerProperty writerProp = new IntegerProperty("writer", 0);
  private final RecordValue<K> inner;

  public WriterKeyValue(final RecordValue<K> inner) {
    super(2);
    this.inner = inner;
    declareProperty(keyProp).declareProperty(writerProp);
  }

  @Override
  public WriterKeyValue<K> wrapValue(final WriterKey<K> key) {
    keyProp.setValue(new UnsafeBuffer(inner.toBytes(key.key())));
    writerProp.setValue(key.writer());
    return this;
  }

  @Override
  public WriterKey<K> value() {
    return new WriterKey<>(
        inner.fromBytes(BufferUtil.bufferAsArray(keyProp.getValue())), writerProp.getValue());
  }
}
