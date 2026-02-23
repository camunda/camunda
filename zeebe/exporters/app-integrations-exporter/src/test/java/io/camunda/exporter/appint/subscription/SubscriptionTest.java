/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.appint.config.BatchConfig;
import io.camunda.exporter.appint.mapper.RecordMapper;
import io.camunda.exporter.appint.transport.Transport;
import io.camunda.zeebe.protocol.record.Record;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class SubscriptionTest {

  private static final int BATCH_SIZE = 3;
  private static final long BATCH_INTERVAL_MS = 500;
  private static final int MAX_BATCHES_IN_FLIGHT = 2;

  private Transport<String> transport;
  private RecordMapper<String> mapper;
  private Consumer<Long> positionConsumer;
  private BatchConfig batchConfig;
  private Subscription<String> subscription;

  @BeforeEach
  void setUp() {
    transport = mock(Transport.class);
    mapper = mock(RecordMapper.class);
    positionConsumer = mock(Consumer.class);
    batchConfig = new BatchConfig(MAX_BATCHES_IN_FLIGHT, BATCH_SIZE, BATCH_INTERVAL_MS, false);
    subscription = new Subscription<>(transport, mapper, batchConfig, positionConsumer);

    // Default behavior for mapper to support all test records and return a non-null value
    // This can be overridden in specific tests as needed
    when(mapper.supports(any())).thenReturn(true);
    when(mapper.map(any())).thenReturn("mapped-value");
  }

  @AfterEach
  void tearDown() {
    subscription.close();
  }

  @Test
  void shouldExportRecordWhenMapperSupportsIt() {
    // given
    final Record<?> record = mockRecord(1L);

    // when
    subscription.exportRecord(record);

    // then
    verify(mapper).supports(record);
    verify(mapper).map(record);
    assertThat(subscription.getBatch().getSize()).isEqualTo(1);
  }

  @Test
  void shouldNotExportRecordWhenMapperDoesNotSupportIt() {
    // given
    final Record<?> record = mockRecord(1L);
    when(mapper.supports(record)).thenReturn(false);

    // when
    subscription.exportRecord(record);

    // then
    verify(mapper).supports(record);
    verify(mapper, never()).map(record);
    assertThat(subscription.getBatch().isEmpty()).isTrue();
  }

  @Test
  void shouldUpdatePositionForUnsupportedRecordWhenNoActiveBatch() {
    // given
    final Record<?> record = mockRecord(1L);
    when(mapper.supports(record)).thenReturn(false);

    // when
    subscription.exportRecord(record);

    // then
    verify(positionConsumer).accept(1L);
  }

  @Test
  void shouldNotUpdatePositionForUnsupportedRecordWhenBatchIsActive() {
    // given
    final Record<?> supportedRecord = mockRecord(1L);
    subscription.exportRecord(supportedRecord);

    final Record<?> unsupportedRecord = mockRecord(2L);
    when(mapper.supports(unsupportedRecord)).thenReturn(false);

    // when
    subscription.exportRecord(unsupportedRecord);

    // then
    verify(positionConsumer, never()).accept(2L);
  }

  @Test
  void shouldFlushBatchWhenFull() {
    // given
    when(mapper.map(any())).thenReturn("mapped-value-1", "mapped-value-2", "mapped-value-3");

    final Record<?> record1 = mockRecord(1L);
    final Record<?> record2 = mockRecord(2L);
    final Record<?> record3 = mockRecord(3L);

    // when
    subscription.exportRecord(record1);
    subscription.exportRecord(record2);
    subscription.exportRecord(record3);

    // then
    Awaitility.await().untilAsserted(() -> verify(positionConsumer).accept(3L));
    assertThat(subscription.getBatch().isEmpty()).isTrue();
  }

  @Test
  void shouldFlushBatchOnAttemptFlushWhenTimeThresholdReached() throws InterruptedException {
    // given
    final Record<?> record = mockRecord(1L);
    when(mapper.map(record)).thenReturn("mapped-value");

    subscription.exportRecord(record);
    assertThat(subscription.getBatch().getSize()).isEqualTo(1);

    // Wait for the batch interval to pass
    Thread.sleep(BATCH_INTERVAL_MS + 100);

    // when
    subscription.attemptFlush();

    // then
    Awaitility.await().untilAsserted(() -> verify(positionConsumer).accept(1L));
    assertThat(subscription.getBatch().isEmpty()).isTrue();
  }

  @Test
  void shouldNotFlushBatchOnAttemptFlushWhenTimeThresholdNotReached() {
    // given
    final Record<?> record = mockRecord(1L);

    subscription.exportRecord(record);

    // when
    subscription.attemptFlush();

    // then
    verify(positionConsumer, never()).accept(1L);
    assertThat(subscription.getBatch().getSize()).isEqualTo(1);
  }

  @Test
  void shouldSendRecordsToTransport() {
    // given
    when(mapper.map(any())).thenReturn("value-1", "value-2", "value-3");

    final Record<?> record1 = mockRecord(1L);
    final Record<?> record2 = mockRecord(2L);
    final Record<?> record3 = mockRecord(3L);

    // when
    subscription.exportRecord(record1);
    subscription.exportRecord(record2);
    subscription.exportRecord(record3);

    // then
    final ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    Awaitility.await().untilAsserted(() -> verify(transport).send(captor.capture()));

    assertThat(captor.getValue()).containsExactly("value-1", "value-2", "value-3");
  }

  @Test
  void shouldThrowExceptionWhenMapperReturnsNull() {
    // given
    final Record<?> record = mockRecord(1L);
    when(mapper.map(record)).thenReturn(null);

    // when / then
    assertThatThrownBy(() -> subscription.exportRecord(record))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Mapper returned null for record at position 1");
  }

  @Test
  void shouldThrowExceptionWhenExportingRecordAfterClose() {
    // given
    final Record<?> record = mockRecord(1L);
    subscription.close();

    // when / then
    assertThatThrownBy(() -> subscription.exportRecord(record))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot export record as subscription is already closed");
  }

  @Test
  void shouldThrowExceptionWhenFlushingAfterClose() {
    // given
    subscription.close();

    // when / then
    assertThatThrownBy(() -> subscription.attemptFlush())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot flush subscription as it is already closed");
  }

  @Test
  void shouldCloseTransportAndDispatcherOnClose() {
    // when
    subscription.close();

    // then
    verify(transport).close();
  }

  @Test
  void shouldRetryBatchOnTransportFailureWhenContinueOnErrorDisabled() {
    // given
    batchConfig = new BatchConfig(MAX_BATCHES_IN_FLIGHT, 1, BATCH_INTERVAL_MS, false);
    subscription = new Subscription<>(transport, mapper, batchConfig, positionConsumer);
    final var counter = new AtomicInteger(0);
    doAnswer(
            i -> {
              if (counter.getAndIncrement() < 2) {
                throw new RuntimeException("Transport error");
              } else {
                return null;
              }
            })
        .when(transport)
        .send(anyList());

    // when
    subscription.exportRecord(mockRecord(1L));

    // then - transport should be called multiple times (retry)
    Awaitility.await().untilAsserted(() -> verify(transport, times(3)).send(anyList()));
    Awaitility.await().untilAsserted(() -> verify(positionConsumer).accept(1L));
  }

  @Test
  void shouldContinueOnTransportFailureWhenContinueOnErrorEnabled() {
    // given
    batchConfig = new BatchConfig(MAX_BATCHES_IN_FLIGHT, 1, BATCH_INTERVAL_MS, true);
    subscription = new Subscription<>(transport, mapper, batchConfig, positionConsumer);

    doThrow(new RuntimeException("Transport error")).when(transport).send(anyList());

    // when
    subscription.exportRecord(mockRecord(1L));

    // then - position should be updated despite transport error
    Awaitility.await().untilAsserted(() -> verify(transport).send(anyList()));
    Awaitility.await().untilAsserted(() -> verify(positionConsumer).accept(1L));
  }

  @Test
  void shouldHandleMultipleBatchesInSequence() {
    // given
    LongStream.range(1, 10)
        .forEach(
            i -> {
              final Record<?> record = mockRecord(i);
              // when
              subscription.exportRecord(record);
            });

    // then
    Awaitility.await().untilAsserted(() -> verify(transport, times(3)).send(anyList()));
    final ArgumentCaptor<Long> argument = ArgumentCaptor.forClass(Long.class);
    Awaitility.await()
        .untilAsserted(() -> verify(positionConsumer, times(3)).accept(argument.capture()));
    Assertions.assertThat(argument.getAllValues()).containsExactly(3L, 6L, 9L);
    Awaitility.await().untilAsserted(() -> subscription.hasNoActiveBatch());
  }

  @Test
  void shouldReportNoActiveBatchWhenEmpty() {
    assertThat(subscription.hasNoActiveBatch()).isTrue();
  }

  private Record<?> mockRecord(final long position) {
    final Record<?> record = mock(Record.class);
    when(record.getPosition()).thenReturn(position);
    return record;
  }
}
