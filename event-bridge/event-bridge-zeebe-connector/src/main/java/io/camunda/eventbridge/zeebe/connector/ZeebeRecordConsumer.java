/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.TopicPartition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Consumes typed Zeebe records from the Event Bridge.
 *
 * <p>A thin typed adapter over {@link Consumer}: {@link #poll(int, Duration)} deserializes each
 * fetched payload back into a Zeebe record with {@link ZeebeRecordCodec}, while group membership
 * and offset commits are delegated unchanged to the underlying consumer.
 */
public final class ZeebeRecordConsumer implements AutoCloseable {

  private final Consumer consumer;
  private final ZeebeRecordCodec codec;

  public ZeebeRecordConsumer(final Consumer consumer) {
    this(consumer, new ZeebeRecordCodec());
  }

  public ZeebeRecordConsumer(final Consumer consumer, final ZeebeRecordCodec codec) {
    this.consumer = consumer;
    this.codec = codec;
  }

  /** Subscribes a new typed consumer to the given topics as a member of {@code groupId}. */
  public static CompletableFuture<ZeebeRecordConsumer> subscribe(
      final EventBridgeClient client,
      final String groupId,
      final String consumerId,
      final List<String> topics) {
    return client.subscribe(groupId, consumerId, topics).thenApply(ZeebeRecordConsumer::new);
  }

  /**
   * Fetches up to {@code maxRecords} records, blocking at most {@code timeout} for them to arrive.
   *
   * <p>Every fetched payload is deserialized eagerly: this consumer exposes no record-type filter,
   * so there is nothing to gate a decode-on-demand peek ({@link ZeebeRecordCodec#accepts}) on —
   * callers that only want a subset filter on the reconstructed record.
   */
  public List<ZeebeRecord> poll(final int maxRecords, final Duration timeout) {
    final List<Event> events = consumer.poll(maxRecords, timeout);
    final List<ZeebeRecord> records = new ArrayList<>(events.size());
    for (int i = 0; i < events.size(); i++) {
      final Event event = events.get(i);
      records.add(
          new ZeebeRecord(
              event.topic(),
              event.partitionId(),
              event.position(),
              codec.deserialize(event.payload())));
    }
    return records;
  }

  /**
   * Resumes each given (topic, partition) from a caller-checkpointed position rather than the
   * committed offset or reset policy. Pass the position after the last durably processed record.
   */
  public void seek(final Map<TopicPartition, Long> startPositions) {
    consumer.seek(startPositions);
  }

  /** Commits progress up to and including the given record. */
  public CompletableFuture<Void> commit(final ZeebeRecord record) {
    return consumer.commitOffset(record.topic(), record.partitionId(), record.offset());
  }

  /** The underlying Event Bridge consumer, for group lifecycle operations (heartbeat, leave). */
  public Consumer unwrap() {
    return consumer;
  }

  @Override
  public void close() {
    consumer.close();
  }
}
