/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import io.camunda.zeebe.protocol.record.Record;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToIntFunction;

/**
 * Publishes typed Zeebe records to an Event Bridge topic.
 *
 * <p>Records are serialized with {@link ZeebeRecordCodec} and routed to a partition by a {@link
 * ToIntFunction} (by default the record's own partition id, which keeps records from a given source
 * partition in order). A {@link #send(Collection) batch} of records is grouped by target partition
 * and published as a single log append per partition — not one record at a time.
 */
public final class ZeebeRecordProducer {

  private final EventBridgeClient client;
  private final ZeebeRecordCodec codec;
  private final String topic;
  private final ToIntFunction<Record<?>> partitioner;

  private ZeebeRecordProducer(
      final EventBridgeClient client,
      final ZeebeRecordCodec codec,
      final String topic,
      final ToIntFunction<Record<?>> partitioner) {
    this.client = client;
    this.codec = codec;
    this.topic = topic;
    this.partitioner = partitioner;
  }

  /** Creates a producer bound to the given topic, routing by the record's own partition id. */
  public static ZeebeRecordProducer to(final EventBridgeClient client, final String topic) {
    return new ZeebeRecordProducer(client, new ZeebeRecordCodec(), topic, Record::getPartitionId);
  }

  /** Returns a copy of this producer that routes records with the given partitioner. */
  public ZeebeRecordProducer withPartitioner(final ToIntFunction<Record<?>> partitioner) {
    return new ZeebeRecordProducer(client, codec, topic, partitioner);
  }

  /** Publishes a single record. */
  public CompletableFuture<Void> send(final Record<?> record) {
    return send(List.of(record));
  }

  /**
   * Publishes a batch of records. Records are grouped by their target partition and each group is
   * published as one batch (a single log append), preserving the order in which records were given
   * for any one partition.
   *
   * @return a future completing when every partition's batch has been acknowledged
   */
  public CompletableFuture<Void> send(final Collection<? extends Record<?>> records) {
    final Map<Integer, List<Record<?>>> byPartition = groupByPartition(records, partitioner);
    final CompletableFuture<?>[] futures =
        byPartition.entrySet().stream()
            .map(entry -> publishPartition(entry.getKey(), entry.getValue()))
            .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(futures);
  }

  private CompletableFuture<List<Long>> publishPartition(
      final int partition, final List<Record<?>> records) {
    final BatchPublisher batch = client.newBatch();
    for (final Record<?> record : records) {
      batch.add(Long.toString(record.getKey()), codec.serialize(record));
    }
    return batch.publishToTopic(topic, partition);
  }

  /** Groups records by their target partition, preserving per-partition order. */
  static Map<Integer, List<Record<?>>> groupByPartition(
      final Collection<? extends Record<?>> records, final ToIntFunction<Record<?>> partitioner) {
    final Map<Integer, List<Record<?>>> grouped = new LinkedHashMap<>();
    for (final Record<?> record : records) {
      grouped
          .computeIfAbsent(partitioner.applyAsInt(record), partition -> new ArrayList<>())
          .add(record);
    }
    return grouped;
  }
}
