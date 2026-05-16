/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.kafka.config.RawConfig;
import io.camunda.exporter.kafka.config.RawProducerConfig;
import io.camunda.exporter.kafka.producer.KafkaRecordPublisher;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class CamundaKafkaExporterTest {

  private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(1);

  private final KafkaRecordPublisher publisher = mock(KafkaRecordPublisher.class);
  private final Controller controller = mock(Controller.class);
  private final ScheduledTask scheduledTask = mock(ScheduledTask.class);
  private final CamundaKafkaExporter exporter = new CamundaKafkaExporter();

  @BeforeEach
  void setUp() {
    when(controller.scheduleCancellableTask(any(), any())).thenReturn(scheduledTask);

    final Context context = mock(Context.class);
    when(context.getLogger()).thenReturn(LoggerFactory.getLogger(getClass()));
    when(context.getPartitionId()).thenReturn(1);

    final Configuration configuration = mock(Configuration.class);
    when(configuration.instantiate(RawConfig.class))
        .thenAnswer(
            inv -> {
              final RawConfig raw = new RawConfig();
              raw.producer = new RawProducerConfig();
              raw.producer.servers = "localhost:9092";
              return raw;
            });
    when(context.getConfiguration()).thenReturn(configuration);

    exporter.configure(context);
    exporter.openWithPublisher(controller, publisher);
  }

  // ---- configure() ----

  @Test
  void shouldSetRecordFilterOnContext() {
    // configure() is called in setUp(); verify the filter was set
    final Context context = mock(Context.class);
    when(context.getLogger()).thenReturn(LoggerFactory.getLogger(getClass()));
    when(context.getPartitionId()).thenReturn(1);

    final Configuration configuration = mock(Configuration.class);
    when(configuration.instantiate(RawConfig.class))
        .thenAnswer(
            inv -> {
              final RawConfig raw = new RawConfig();
              raw.producer = new RawProducerConfig();
              raw.producer.servers = "localhost:9092";
              return raw;
            });
    when(context.getConfiguration()).thenReturn(configuration);

    new CamundaKafkaExporter().configure(context);

    // then — the filter must have been installed
    verify(context).setFilter(any(RecordFilter.class));
  }

  // ---- export() — partition-scope filtering ----

  @Test
  void shouldNotPublishDefinitionRecordFromNonFirstPartition() {
    // given — PROCESS is a definition type; only exported from partition 1
    final Record<?> record = mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.PROCESS);
    when(record.getPartitionId()).thenReturn(2);
    when(record.getValue()).thenReturn(mock(RecordValue.class));

    // when
    exporter.export(record);

    // then — excluded by partition scope; publisher must not be called
    verify(publisher, never()).publish(any());
  }

  @Test
  void shouldPublishDefinitionRecordFromPartitionOne() {
    // given — PROCESS on partition 1 is allowed
    final Record<?> record = mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.PROCESS);
    when(record.getPartitionId()).thenReturn(1);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getIntent()).thenReturn(JobIntent.CREATED);
    when(record.getBrokerVersion()).thenReturn("8.10.0");
    when(record.getPosition()).thenReturn(1L);
    when(record.getValue()).thenReturn(mock(RecordValue.class));

    // when
    exporter.export(record);

    // then
    verify(publisher).publish(any());
  }

  @Test
  void shouldPublishRuntimeRecordFromAnyPartition() {
    // given — JOB is a runtime type; allowed on any partition
    final Record<?> record = mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.JOB);
    when(record.getPartitionId()).thenReturn(3);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getIntent()).thenReturn(JobIntent.CREATED);
    when(record.getBrokerVersion()).thenReturn("8.10.0");
    when(record.getPosition()).thenReturn(42L);
    final JobRecordValue value = mock(JobRecordValue.class);
    when(value.toJson()).thenReturn("{}");
    when(record.getValue()).thenReturn(value);

    // when
    exporter.export(record);

    // then
    verify(publisher).publish(any());
  }

  // ---- close() ----

  @Test
  void shouldCancelScheduledTaskOnClose() {
    // when
    exporter.close();

    // then
    verify(scheduledTask).cancel();
  }

  @Test
  void shouldUpdatePositionOnCloseWhenPositivePositionAvailable() {
    // given
    when(publisher.getLastFlushedPosition()).thenReturn(42L);

    // when
    exporter.close();

    // then
    verify(publisher).close();
    verify(controller).updateLastExportedRecordPosition(42L);
  }

  @Test
  void shouldNotUpdatePositionOnCloseWhenNoFlushHasOccurred() {
    // given — no flush yet
    when(publisher.getLastFlushedPosition()).thenReturn(-1L);

    // when
    exporter.close();

    // then — -1 means "no flush" so position must not be updated
    verify(controller, never()).updateLastExportedRecordPosition(any(Long.class));
  }

  // ---- checkAndUpdatePosition() ----

  @Test
  void shouldOnlyReportPositionWhenItAdvances() {
    // given — position is 10
    when(publisher.getLastFlushedPosition()).thenReturn(10L);

    // when — first call
    exporter.checkAndUpdatePosition();

    // then — reported once
    verify(controller).updateLastExportedRecordPosition(10L);

    // when — second call with same position
    exporter.checkAndUpdatePosition();

    // then — NOT reported again (position did not advance)
    verify(controller).updateLastExportedRecordPosition(eq(10L)); // still only 1 invocation total
  }

  @Test
  void shouldReschedulePositionUpdateAfterEachCheck() {
    // given — one call from openWithPublisher() + one manual call
    when(publisher.getLastFlushedPosition()).thenReturn(-1L);

    exporter.checkAndUpdatePosition();

    // then — scheduleCancellableTask called: once in openWithPublisher + once in
    // checkAndUpdatePosition
    verify(controller, org.mockito.Mockito.times(2))
        .scheduleCancellableTask(eq(FLUSH_INTERVAL), any());
  }

  @Test
  void shouldHandleCloseBeforeOpen() {
    // given — a fresh exporter that was never opened
    final CamundaKafkaExporter freshExporter = new CamundaKafkaExporter();
    final Context context = mock(Context.class);
    when(context.getLogger()).thenReturn(LoggerFactory.getLogger(getClass()));
    when(context.getPartitionId()).thenReturn(1);
    final Configuration configuration = mock(Configuration.class);
    when(configuration.instantiate(RawConfig.class))
        .thenAnswer(
            inv -> {
              final RawConfig raw = new RawConfig();
              raw.producer = new RawProducerConfig();
              raw.producer.servers = "localhost:9092";
              return raw;
            });
    when(context.getConfiguration()).thenReturn(configuration);
    freshExporter.configure(context);

    // when / then — close() before open() must not throw NPE
    org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(freshExporter::close);
  }
}
