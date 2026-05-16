/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.kafka.config.OverflowPolicy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unchecked")
final class KafkaRecordPublisherTest {

  private static final String TOPIC = "zeebe";
  private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(1);
  private static final long CLOSE_TIMEOUT_MS = 5_000;

  private KafkaProducer<String, String> producer;
  private KafkaRecordPublisher publisher;

  @BeforeEach
  void setUp() {
    producer = mock(KafkaProducer.class);
    when(producer.partitionsFor(TOPIC)).thenReturn(threePartitions());
    publisher = newPublisher(100, 1_000, OverflowPolicy.DROP_OLDEST);
  }

  // ---- Ordering ----

  @Test
  void shouldFlushRecordsInPublishOrder() {
    // given
    publisher.publish(record(1, 10L, "v1"));
    publisher.publish(record(1, 20L, "v2"));
    publisher.publish(record(1, 30L, "v3"));

    // when
    publisher.flush();

    // then — values must arrive at Kafka in publish order
    final var inOrder = inOrder(producer);
    inOrder.verify(producer).send(recordWithValue("v1"));
    inOrder.verify(producer).send(recordWithValue("v2"));
    inOrder.verify(producer).send(recordWithValue("v3"));
  }

  @Test
  void shouldPreservePublishOrderAfterTransactionFailureAndRetry() {
    // given — first commit fails, second succeeds
    doThrow(new RuntimeException("broker unavailable"))
        .doNothing()
        .when(producer)
        .commitTransaction();

    publisher.publish(record(1, 10L, "v1"));
    publisher.publish(record(1, 20L, "v2"));

    // when
    publisher.flush(); // transaction aborted, records re-enqueued
    publisher.flush(); // succeeds

    // then — records re-sent in the same order on retry
    final var captor = sentRecordsCaptor();
    verify(producer, times(4)).send(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(ProducerRecord::value)
        .containsExactly("v1", "v2", "v1", "v2");
    assertThat(publisher.getLastFlushedPosition()).isEqualTo(20L);
  }

  // ---- Transaction semantics ----

  @Test
  void shouldBeginAndCommitTransactionOnEachFlush() {
    // given
    publisher.publish(record(1, 10L, "v1"));

    // when
    publisher.flush();

    // then
    final var inOrder = inOrder(producer);
    inOrder.verify(producer).beginTransaction();
    inOrder.verify(producer).send(any());
    inOrder.verify(producer).commitTransaction();
  }

  @Test
  void shouldAbortTransactionAndReenqueueBatchOnCommitFailure() {
    // given
    doThrow(new RuntimeException("commit failed")).doNothing().when(producer).commitTransaction();
    publisher.publish(record(1, 10L, "v1"));
    publisher.publish(record(1, 20L, "v2"));

    // when
    publisher.flush();

    // then — transaction aborted, position not advanced
    verify(producer).abortTransaction();
    assertThat(publisher.getLastFlushedPosition()).isEqualTo(-1L);

    // and records are available for retry — second commitTransaction() is a no-op (success)
    publisher.flush();
    verify(producer, times(4)).send(any()); // 2 sends per attempt
    assertThat(publisher.getLastFlushedPosition()).isEqualTo(20L);
  }

  @Test
  void shouldAbortTransactionAndReenqueueBatchWhenPartitionMetadataUnavailable() {
    // given — first call throws (e.g. topic not yet created), second succeeds
    when(producer.partitionsFor(TOPIC))
        .thenThrow(new RuntimeException("metadata unavailable"))
        .thenReturn(threePartitions());
    publisher.publish(record(1, 10L, "v1"));
    publisher.publish(record(1, 20L, "v2"));

    // when
    publisher.flush(); // fails at partitionsFor on first record

    // then — entire batch aborted (no records reached Kafka)
    verify(producer, never()).send(any());
    verify(producer).abortTransaction();
    assertThat(publisher.getLastFlushedPosition()).isEqualTo(-1L);

    // and the full batch is retried in order
    publisher.flush();
    final var captor = sentRecordsCaptor();
    verify(producer, times(2)).send(captor.capture());
    assertThat(captor.getAllValues()).extracting(ProducerRecord::value).containsExactly("v1", "v2");
    assertThat(publisher.getLastFlushedPosition()).isEqualTo(20L);
  }

  @Test
  void shouldNotAdvancePositionOnFlushFailure() {
    // given
    doThrow(new RuntimeException("send failed")).when(producer).commitTransaction();
    publisher.publish(record(1, 10L, "v1"));

    // when
    publisher.flush();

    // then
    assertThat(publisher.getLastFlushedPosition()).isEqualTo(-1L);
  }

  @Test
  void shouldAdvancePositionToMaxPositionInBatch() {
    // given — records from the same Zeebe partition with non-monotonic positions.
    // In production each exporter instance is bound to one partition, so zeebePartitionId is
    // always the same. Non-monotonic positions CAN occur when definition records (exported only
    // from partition 1) are interleaved with runtime records in the queue.
    publisher.publish(record(1, 30L, "v1"));
    publisher.publish(record(1, 10L, "v2"));
    publisher.publish(record(1, 20L, "v3"));

    // when
    publisher.flush();

    // then — position is the max across the batch, not just the last record's position
    assertThat(publisher.getLastFlushedPosition()).isEqualTo(30L);
  }

  // ---- Batch size ----

  @Test
  void shouldRespectMaxBatchSizeAndLeaveRemaindersForNextFlush() {
    // given — batch size 2, 4 records published
    final var smallBatch = newPublisher(2, 100, OverflowPolicy.DROP_OLDEST);
    smallBatch.publish(record(1, 10L, "v1"));
    smallBatch.publish(record(1, 20L, "v2"));
    smallBatch.publish(record(1, 30L, "v3"));
    smallBatch.publish(record(1, 40L, "v4"));

    // when
    smallBatch.flush();

    // then — only first 2 sent
    verify(producer, times(2)).send(any());
    assertThat(smallBatch.getLastFlushedPosition()).isEqualTo(20L);

    // and remaining 2 sent on next flush
    smallBatch.flush();
    verify(producer, times(4)).send(any());
    assertThat(smallBatch.getLastFlushedPosition()).isEqualTo(40L);
  }

  // ---- Overflow policies ----

  @Test
  void shouldDropOldestRecordWhenQueueFull() {
    // given — queue capacity 2
    final var p = newPublisher(100, 2, OverflowPolicy.DROP_OLDEST);
    p.publish(record(1, 10L, "v1")); // oldest
    p.publish(record(1, 20L, "v2"));
    p.publish(record(1, 30L, "v3")); // v1 dropped to make room

    // when
    p.flush();

    // then
    final var captor = sentRecordsCaptor();
    verify(producer, times(2)).send(captor.capture());
    assertThat(captor.getAllValues()).extracting(ProducerRecord::value).containsExactly("v2", "v3");
  }

  @Test
  void shouldDropNewestRecordWhenQueueFull() {
    // given — queue capacity 2
    final var p = newPublisher(100, 2, OverflowPolicy.DROP_NEWEST);
    p.publish(record(1, 10L, "v1"));
    p.publish(record(1, 20L, "v2"));
    p.publish(record(1, 30L, "v3")); // v3 dropped — queue already full

    // when
    p.flush();

    // then
    final var captor = sentRecordsCaptor();
    verify(producer, times(2)).send(captor.capture());
    assertThat(captor.getAllValues()).extracting(ProducerRecord::value).containsExactly("v1", "v2");
  }

  @Test
  void shouldBlockPublishUntilQueueDrains() {
    // given — queue capacity 1, already full
    final var p = newPublisher(100, 1, OverflowPolicy.BLOCK);
    p.publish(record(1, 10L, "v1"));

    // a second publish should block until flush frees space
    final var publishThread = Thread.ofPlatform().start(() -> p.publish(record(1, 20L, "v2")));

    // BLOCK policy uses wait(timeout) which results in TIMED_WAITING, not indefinite WAITING.
    await()
        .atMost(1, TimeUnit.SECONDS)
        .until(() -> publishThread.getState() == Thread.State.TIMED_WAITING);

    // when — flush drains v1, unblocking v2's publish
    p.flush();

    // then — publish thread completes
    await()
        .atMost(1, TimeUnit.SECONDS)
        .until(() -> publishThread.getState() == Thread.State.TERMINATED);
    assertThat(p.getLastFlushedPosition()).isEqualTo(10L);
  }

  // ---- beginTransaction failure ----

  @Test
  void shouldNotCallAbortTransactionWhenBeginTransactionFails() {
    // given — beginTransaction throws before a transaction is open
    doThrow(new RuntimeException("broker fenced")).doNothing().when(producer).beginTransaction();
    publisher.publish(record(1, 10L, "v1"));

    // when
    publisher.flush(); // beginTransaction fails — no transaction to abort

    // then — abortTransaction must NOT be called (there is no open transaction to abort)
    verify(producer, never()).abortTransaction();
    assertThat(publisher.getLastFlushedPosition()).isEqualTo(-1L);

    // and the record is re-enqueued and sent successfully on retry
    publisher.flush();
    verify(producer, times(1)).send(any());
    assertThat(publisher.getLastFlushedPosition()).isEqualTo(10L);
  }

  // ---- Multi-topic batches ----

  @Test
  void shouldCallPartitionsForOncePerDistinctTopicPerFlush() {
    // given — two records on different topics in one batch
    final String secondTopic = "zeebe-job";
    when(producer.partitionsFor(secondTopic))
        .thenReturn(
            List.of(
                new PartitionInfo(secondTopic, 0, null, new Node[0], new Node[0]),
                new PartitionInfo(secondTopic, 1, null, new Node[0], new Node[0])));

    publisher.publish(record(1, 10L, "v1")); // TOPIC
    publisher.publish(
        new KafkaExportRecord(secondTopic, "k2", "v2", Map.of(), 1, 20L)); // secondTopic
    publisher.publish(record(1, 30L, "v3")); // TOPIC again

    // when
    publisher.flush();

    // then — partitionsFor called once per distinct topic, not once per record
    verify(producer, times(1)).partitionsFor(TOPIC);
    verify(producer, times(1)).partitionsFor(secondTopic);
  }

  // ---- Start guard ----

  @Test
  void shouldThrowWhenStartCalledTwice() {
    // given
    publisher.start();

    // when / then
    org.assertj.core.api.Assertions.assertThatThrownBy(publisher::start)
        .isInstanceOf(IllegalStateException.class);

    // cleanup
    publisher.close();
  }

  // ---- Shutdown ----

  @Test
  void shouldFlushRemainingRecordsOnClose() {
    // given
    publisher.publish(record(1, 10L, "v1"));
    publisher.publish(record(1, 20L, "v2"));

    // when — close without explicit flush
    publisher.close();

    // then
    final var captor = sentRecordsCaptor();
    verify(producer, times(2)).send(captor.capture());
    assertThat(captor.getAllValues()).extracting(ProducerRecord::value).containsExactly("v1", "v2");
    assertThat(publisher.getLastFlushedPosition()).isEqualTo(20L);
  }

  // ---- Helpers ----

  private KafkaRecordPublisher newPublisher(
      final int maxBatchSize, final int maxQueueSize, final OverflowPolicy policy) {
    return new KafkaRecordPublisher(
        producer,
        maxBatchSize,
        maxQueueSize,
        policy,
        FLUSH_INTERVAL,
        LoggerFactory.getLogger(getClass()),
        CLOSE_TIMEOUT_MS);
  }

  private static KafkaExportRecord record(
      final int zeebePartitionId, final long position, final String value) {
    return new KafkaExportRecord(
        TOPIC, zeebePartitionId + "-" + position, value, Map.of(), zeebePartitionId, position);
  }

  private ArgumentCaptor<ProducerRecord<String, String>> sentRecordsCaptor() {
    return ArgumentCaptor.forClass(
        (Class<ProducerRecord<String, String>>) (Class<?>) ProducerRecord.class);
  }

  private static ProducerRecord<String, String> recordWithValue(final String value) {
    return org.mockito.ArgumentMatchers.argThat(r -> value.equals(r.value()));
  }

  private static List<PartitionInfo> threePartitions() {
    return List.of(
        new PartitionInfo(TOPIC, 0, null, new Node[0], new Node[0]),
        new PartitionInfo(TOPIC, 1, null, new Node[0], new Node[0]),
        new PartitionInfo(TOPIC, 2, null, new Node[0], new Node[0]));
  }
}
