/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalyticsExporterSequenceNumberTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  private InMemoryLogRecordExporter memoryExporter;
  private ExporterTestController controller;
  private AnalyticsExporter exporter;

  @BeforeEach
  void setUp() {
    memoryExporter = InMemoryLogRecordExporter.create();
    controller = new ExporterTestController();
    exporter = exporterWithInMemory(memoryExporter, controller);
  }

  @Test
  void shouldIncrementSequenceNumberWithEachHandledEvent() {
    // given / when
    exporter.export(piCreatedEvent());
    exporter.export(piCreatedEvent());
    exporter.export(piCreatedEvent());

    // then
    final var logs = memoryExporter.getFinishedLogRecordItems();
    assertThat(logs).hasSize(3);
    assertThat(logs.get(0).getAttributes().get(AnalyticsAttributes.SEQUENCE_NUMBER)).isEqualTo(1L);
    assertThat(logs.get(1).getAttributes().get(AnalyticsAttributes.SEQUENCE_NUMBER)).isEqualTo(2L);
    assertThat(logs.get(2).getAttributes().get(AnalyticsAttributes.SEQUENCE_NUMBER)).isEqualTo(3L);
  }

  @Test
  void shouldNotIncrementSequenceNumberForUnhandledRecordTypes() {
    // given
    final var unhandled =
        FACTORY.generateRecord(ValueType.JOB, r -> r.withRecordType(RecordType.EVENT));

    // when
    exporter.export(unhandled);
    exporter.export(piCreatedEvent());

    // then
    assertThat(memoryExporter.getFinishedLogRecordItems())
        .singleElement()
        .extracting(log -> log.getAttributes().get(AnalyticsAttributes.SEQUENCE_NUMBER))
        .isEqualTo(1L);
  }

  @Test
  void shouldPersistSequenceNumberInMetadata() {
    // given / when
    exporter.export(piCreatedEvent());
    exporter.export(piCreatedEvent());
    exporter.export(piCreatedEvent());

    // then
    assertThat(controller.readMetadata())
        .isPresent()
        .get()
        .satisfies(
            bytes ->
                assertThat(AnalyticsExporterMetadata.deserialize(bytes).getSequenceNumber())
                    .isEqualTo(3L));
  }

  @Test
  void shouldRestoreSequenceNumberFromMetadataOnOpen() {
    // given — controller pre-seeded with persisted sequence number 5
    final var seededController = new ExporterTestController();
    seededController.updateLastExportedRecordPosition(
        0L, new AnalyticsExporterMetadata(5L).serialize());
    final var freshMemoryExporter = InMemoryLogRecordExporter.create();
    final var freshExporter = exporterWithInMemory(freshMemoryExporter, seededController);

    // when
    freshExporter.export(piCreatedEvent());

    // then
    assertThat(freshMemoryExporter.getFinishedLogRecordItems())
        .singleElement()
        .extracting(log -> log.getAttributes().get(AnalyticsAttributes.SEQUENCE_NUMBER))
        .isEqualTo(6L);
  }

  @Test
  void shouldIncrementSequenceNumberEvenWhenOtelQueueIsFull() {
    // given — tiny queue (1 event) with a blocking exporter so the queue stays full
    final var blockExport = new CountDownLatch(1);
    final var fullQueueController = new ExporterTestController();
    final var fullQueueExporter =
        exporterWithBatchLogProcessor(
            fullQueueController,
            logs -> {
              try {
                blockExport.await();
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return CompletableResultCode.ofSuccess();
            },
            1,
            1);

    // when — flood events well beyond queue capacity (explicit positions to ensure metadata
    // updates)
    for (int i = 0; i < 20; i++) {
      fullQueueExporter.export(piCreatedEvent(i + 1));
    }

    // then — all 20 exports incremented the sequence, regardless of how many were dropped
    assertThat(fullQueueController.readMetadata())
        .isPresent()
        .get()
        .satisfies(
            bytes ->
                assertThat(AnalyticsExporterMetadata.deserialize(bytes).getSequenceNumber())
                    .isEqualTo(20L));

    blockExport.countDown();
    fullQueueExporter.close();
  }

  @Test
  void shouldIncrementSequenceNumberEvenWhenExportBatchFails() {
    // given — exporter that always fails (simulates network issues)
    final var failingController = new ExporterTestController();
    final var failingExporter =
        exporterWithBatchLogProcessor(
            failingController, logs -> CompletableResultCode.ofFailure(), 2048, 512);

    // when (explicit positions to ensure metadata updates)
    for (int i = 0; i < 10; i++) {
      failingExporter.export(piCreatedEvent(i + 1));
    }
    failingExporter.close();

    // then — sequence is incremented for all events regardless of backend delivery failures
    assertThat(failingController.readMetadata())
        .isPresent()
        .get()
        .satisfies(
            bytes ->
                assertThat(AnalyticsExporterMetadata.deserialize(bytes).getSequenceNumber())
                    .isEqualTo(10L));
  }

  // -- helpers --

  private static io.camunda.zeebe.protocol.record.Record<?> piCreatedEvent() {
    return FACTORY.generateRecord(
        ValueType.PROCESS_INSTANCE_CREATION,
        r -> r.withRecordType(RecordType.EVENT).withIntent(ProcessInstanceCreationIntent.CREATED));
  }

  private static io.camunda.zeebe.protocol.record.Record<?> piCreatedEvent(final long position) {
    return FACTORY.generateRecord(
        ValueType.PROCESS_INSTANCE_CREATION,
        r ->
            r.withRecordType(RecordType.EVENT)
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withPosition(position));
  }

  private static AnalyticsExporter exporterWithInMemory(
      final InMemoryLogRecordExporter memoryExporter, final ExporterTestController controller) {
    return newExporter(
        new OtelSdkManager() {
          @Override
          protected SdkLoggerProvider createLoggerProvider(
              final AnalyticsExporterConfig cfg, final Resource resource) {
            return SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(memoryExporter))
                .build();
          }
        },
        controller);
  }

  private static AnalyticsExporter exporterWithBatchLogProcessor(
      final ExporterTestController controller,
      final Function<Collection<LogRecordData>, CompletableResultCode> exportFn,
      final int maxQueueSize,
      final int maxBatchSize) {
    return newExporter(
        new OtelSdkManager() {
          @Override
          protected SdkLoggerProvider createLoggerProvider(
              final AnalyticsExporterConfig cfg, final Resource resource) {
            return SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(
                    BatchLogRecordProcessor.builder(logExporterFrom(exportFn))
                        .setMaxQueueSize(maxQueueSize)
                        .setMaxExportBatchSize(maxBatchSize)
                        .setScheduleDelay(Duration.ofMillis(100))
                        .build())
                .build();
          }
        },
        controller);
  }

  private static LogRecordExporter logExporterFrom(
      final Function<Collection<LogRecordData>, CompletableResultCode> exportFn) {
    return new LogRecordExporter() {
      @Override
      public CompletableResultCode export(final Collection<LogRecordData> logs) {
        return exportFn.apply(logs);
      }

      @Override
      public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
      }

      @Override
      public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
      }
    };
  }

  private static AnalyticsExporter newExporter(
      final OtelSdkManager otelSdkManager, final ExporterTestController controller) {
    final var context =
        new ExporterTestContext()
            .setConfiguration(
                new ExporterTestConfiguration<>("analytics", new AnalyticsExporterConfig()))
            .setClusterId("test-cluster")
            .setPartitionId(1);
    final var exporter = new AnalyticsExporter(otelSdkManager);
    exporter.configure(context);
    exporter.open(controller);
    return exporter;
  }
}
