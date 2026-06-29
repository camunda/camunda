/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.EventBridgeClient;
import java.time.Duration;
import java.util.List;
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
   */
  public List<ZeebeRecord> poll(final int maxRecords, final Duration timeout) {
    return consumer.poll(maxRecords, timeout).stream()
        .map(
            event ->
                new ZeebeRecord(
                    event.topic(),
                    event.partitionId(),
                    event.position(),
                    codec.deserialize(event.payload(), event.partitionId(), event.position())))
        .toList();
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
