/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.shuffle;

import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.window.Windowed;

/**
 * The Stage-1 combiner's sink: instead of writing to a serving store, it publishes each changed
 * cell's current full value as a {@link Partial} to the facts topic (the shuffle). The combiner is
 * a {@code DurableMaterializedAggregation} keyed by {@link WriterKey}, so every cell carries its
 * writer (source partition); this sink strips the writer back out of the key, tags the partial with
 * it, and routes by the <em>inner</em> key so all writers of a cell reach one Stage-2 reducer.
 *
 * @param <K> the base grouping key type
 * @param <ACC> the accumulator type
 */
public final class FactPublishSink<K, ACC> implements ResultSink<Windowed<WriterKey<K>>, ACC> {

  private final int aggId;
  private final RecordValue<K> keyValue;
  private final RecordValue<ACC> accValue;
  private final PartialPublisher publisher;

  public FactPublishSink(
      final int aggId,
      final RecordValue<K> keyValue,
      final RecordValue<ACC> accValue,
      final PartialPublisher publisher) {
    this.aggId = aggId;
    this.keyValue = keyValue;
    this.accValue = accValue;
    this.publisher = publisher;
  }

  @Override
  public void upsert(final Windowed<WriterKey<K>> windowed, final ACC value) {
    final WriterKey<K> writerKey = windowed.key();
    publisher.publish(
        new Partial(
            aggId,
            keyValue.toBytes(writerKey.key()),
            windowed.windowStart(),
            writerKey.writer(),
            accValue.toBytes(value)));
  }
}
