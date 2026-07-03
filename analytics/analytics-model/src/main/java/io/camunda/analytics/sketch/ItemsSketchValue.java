/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.datasketches.common.ArrayOfStringsSerDe;
import org.apache.datasketches.frequencies.ItemsSketch;
import org.apache.datasketches.memory.Memory;

/**
 * Record flyweight for the {@link TopKAggregateFunction} accumulator: a frequent-items sketch of
 * {@code String} items held in a single {@link BinaryProperty} as its own serialized form.
 * Round-tripping yields a functionally equivalent sketch (same frequencies); equivalence is by
 * frequency estimate rather than {@code equals}.
 */
public final class ItemsSketchValue extends UnpackedObject
    implements RecordValue<ItemsSketch<String>> {

  private final ArrayOfStringsSerDe serde = new ArrayOfStringsSerDe();
  private final BinaryProperty sketchProp = new BinaryProperty("sketch");

  public ItemsSketchValue() {
    super(1);
    declareProperty(sketchProp);
  }

  @Override
  public ItemsSketchValue wrapValue(final ItemsSketch<String> sketch) {
    sketchProp.setValue(new UnsafeBuffer(sketch.toByteArray(serde)));
    return this;
  }

  @Override
  public ItemsSketch<String> value() {
    return ItemsSketch.getInstance(
        Memory.wrap(BufferUtil.bufferAsArray(sketchProp.getValue())), serde);
  }
}
