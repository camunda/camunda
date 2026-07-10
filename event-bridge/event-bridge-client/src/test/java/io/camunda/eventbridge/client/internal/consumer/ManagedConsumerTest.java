/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.Deserializer;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.MessageHandler;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies {@link ManagedConsumer} dispatch and per-batch auto-commit of the max offset. */
final class ManagedConsumerTest {

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void shouldDispatchRecordsAndAutoCommitMaxOffsetPerPartition() throws InterruptedException {
    // given: a consumer that returns one batch spanning two partitions, then empty batches
    final Consumer consumer = mock(Consumer.class);
    when(consumer.getGroupId()).thenReturn("g1");

    // Two distinct partitions are committed, so latch on two commit invocations.
    final CountDownLatch committed = new CountDownLatch(2);
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            inv -> {
              committed.countDown();
              return CompletableFuture.completedFuture(null);
            });

    final List<Event> firstBatch =
        List.of(
            new Event(10L, "t1", 1, "a".getBytes(StandardCharsets.UTF_8)),
            new Event(11L, "t1", 1, "b".getBytes(StandardCharsets.UTF_8)),
            new Event(20L, "t1", 2, "c".getBytes(StandardCharsets.UTF_8)));
    when(consumer.poll(anyInt(), any(Duration.class))).thenReturn(firstBatch).thenReturn(List.of());

    final List<Event> handled = new CopyOnWriteArrayList<>();
    final MessageHandler<Event> handler = handled::add;

    // when
    final ManagedConsumer<Event> managed =
        new ManagedConsumer<>(consumer, executor, handler, null, true, 100, Duration.ofMillis(10));
    try {
      assertThat(committed.await(5, TimeUnit.SECONDS)).isTrue();

      // then: every record was dispatched and the max offset per partition was committed
      assertThat(handled).containsExactlyInAnyOrderElementsOf(firstBatch);
      verify(consumer, atLeast(1)).commitOffset(eq("t1"), eq(1), eq(11L));
      verify(consumer, atLeast(1)).commitOffset(eq("t1"), eq(2), eq(20L));
    } finally {
      managed.close();
    }

    // then: close also closed the underlying consumer
    verify(consumer).close();
  }

  @Test
  void shouldStopBeforeCommittingAFailedRecordSoItIsRedelivered() throws InterruptedException {
    // given: a batch of three records on one partition whose SECOND record's handler throws
    final Consumer consumer = mock(Consumer.class);
    when(consumer.getGroupId()).thenReturn("g1");
    final CountDownLatch committed = new CountDownLatch(1);
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            inv -> {
              committed.countDown();
              return CompletableFuture.completedFuture(null);
            });
    final List<Event> batch =
        List.of(
            new Event(10L, "t1", 1, "a".getBytes(StandardCharsets.UTF_8)),
            new Event(11L, "t1", 1, "b".getBytes(StandardCharsets.UTF_8)),
            new Event(12L, "t1", 1, "c".getBytes(StandardCharsets.UTF_8)));
    when(consumer.poll(anyInt(), any(Duration.class))).thenReturn(batch).thenReturn(List.of());

    final List<Event> handled = new CopyOnWriteArrayList<>();
    final MessageHandler<Event> handler =
        event -> {
          if (event.position() == 11L) {
            throw new IllegalStateException("boom");
          }
          handled.add(event);
        };

    // when
    final ManagedConsumer<Event> managed =
        new ManagedConsumer<>(consumer, executor, handler, null, true, 100, Duration.ofMillis(10));
    try {
      assertThat(committed.await(5, TimeUnit.SECONDS)).isTrue();

      // then: the loop stopped at the failure — the commit covers ONLY the record that succeeded,
      // so the failed record (and everything after it) is redelivered on resume, never dropped
      verify(consumer, atLeast(1)).commitOffset(eq("t1"), eq(1), eq(10L));
      verify(consumer, never()).commitOffset(eq("t1"), eq(1), eq(11L));
      verify(consumer, never()).commitOffset(eq("t1"), eq(1), eq(12L));
      assertThat(handled).containsExactly(batch.get(0));
    } finally {
      managed.close();
    }
  }

  @Test
  void shouldApplyDeserializerBeforeDispatch() throws InterruptedException {
    // given
    final Consumer consumer = mock(Consumer.class);
    when(consumer.getGroupId()).thenReturn("g1");
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(consumer.poll(anyInt(), any(Duration.class)))
        .thenReturn(List.of(new Event(1L, "t1", 1, "hello".getBytes(StandardCharsets.UTF_8))))
        .thenReturn(List.of());

    final List<String> handled = new CopyOnWriteArrayList<>();
    final CountDownLatch dispatched = new CountDownLatch(1);
    final Deserializer<String> deserializer =
        (payload, partition, offset) -> new String(payload, StandardCharsets.UTF_8) + "@" + offset;
    final MessageHandler<String> handler =
        record -> {
          handled.add(record);
          dispatched.countDown();
        };

    // when
    final ManagedConsumer<String> managed =
        new ManagedConsumer<>(
            consumer, executor, handler, deserializer, false, 100, Duration.ofMillis(10));
    try {
      assertThat(dispatched.await(5, TimeUnit.SECONDS)).isTrue();

      // then: the payload was deserialized to the typed value before dispatch
      assertThat(new ArrayList<>(handled)).containsExactly("hello@1");
    } finally {
      managed.close();
    }
  }
}
