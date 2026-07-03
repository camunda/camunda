/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import io.camunda.eventbridge.streaming.aggregate.Codec;
import org.apache.datasketches.common.ArrayOfStringsSerDe;
import org.apache.datasketches.frequencies.ItemsSketch;
import org.apache.datasketches.memory.Memory;

/**
 * Byte codec for the {@link TopKAggregateFunction} accumulator, so the durable rollup can persist a
 * frequent-items sketch of {@code String} items as a cell value. Serializes with the sketch's own
 * form and a string serde; round-tripping yields a functionally equivalent sketch (same
 * frequencies) — equivalence is by frequency estimate rather than by {@code equals}.
 */
public final class ItemsSketchCodec implements Codec<ItemsSketch<String>> {

  private final ArrayOfStringsSerDe serde = new ArrayOfStringsSerDe();

  @Override
  public byte[] encode(final ItemsSketch<String> sketch) {
    return sketch.toByteArray(serde);
  }

  @Override
  public ItemsSketch<String> decode(final byte[] bytes) {
    return ItemsSketch.getInstance(Memory.wrap(bytes), serde);
  }
}
