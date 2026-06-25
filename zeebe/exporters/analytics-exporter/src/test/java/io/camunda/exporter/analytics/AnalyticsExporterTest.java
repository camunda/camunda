/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalyticsExporterTest {

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
  void shouldRejectMissingLicenseKey() {
    // given
    final var context =
        new ExporterTestContext()
            .setConfiguration(
                new ExporterTestConfiguration<>("analytics", new AnalyticsExporterConfig()));

    // when / then
    assertThatThrownBy(() -> new AnalyticsExporter().configure(context))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CAMUNDA_LICENSE_KEY");
  }

  @Test
  void shouldEmitLogRecordWithAllAttributes() {
    // given
    final var record = piCreatedEvent();

    // when
    exporter.export(record);

    // then
    final var value = (ProcessInstanceCreationRecordValue) record.getValue();
    assertThat(memoryExporter.getFinishedLogRecordItems())
        .singleElement()
        .satisfies(
            logRecord -> {
              assertThat(logRecord.getSeverity()).isEqualTo(Severity.INFO);
              assertThat(logRecord.getAttributes().asMap())
                  .containsEntry(
                      AnalyticsAttributes.Event.NAME,
                      AnalyticsAttributes.Event.PROCESS_INSTANCE_CREATED)
                  .containsEntry(
                      AnalyticsAttributes.Process.BPMN_PROCESS_ID, value.getBpmnProcessId())
                  .containsEntry(AnalyticsAttributes.Process.VERSION, (long) value.getVersion())
                  .containsEntry(
                      AnalyticsAttributes.Process.DEFINITION_KEY, value.getProcessDefinitionKey())
                  .containsEntry(AnalyticsAttributes.Process.INSTANCE_KEY, record.getKey())
                  // Temporarily commented out as root process instance key doesn't exist on 8.8.
                  //                  .containsEntry(
                  //                      AnalyticsAttributes.Process.ROOT_INSTANCE_KEY,
                  //                      value.getRootProcessInstanceKey())
                  .containsEntry(AnalyticsAttributes.Tenant.ID, value.getTenantId())
                  .containsEntry(AnalyticsAttributes.Log.POSITION, record.getPosition())
                  .containsEntry(AnalyticsAttributes.Event.SEQUENCE_NUMBER, 1L);
            });
  }

  @Test
  void shouldUpdatePositionForUnhandledEventType() {
    // given
    final var record =
        FACTORY.generateRecord(ValueType.JOB, r -> r.withRecordType(RecordType.EVENT));

    // when
    exporter.export(record);

    // then
    assertThat(controller.getPosition()).isEqualTo(record.getPosition());
    assertThat(memoryExporter.getFinishedLogRecordItems()).isEmpty();
  }

  @Test
  void shouldEmitMultipleEventsInSequence() {
    // given / when
    final var positions = new ArrayList<Long>(10);
    for (int i = 0; i < 10; i++) {
      final var record = piCreatedEvent();
      exporter.export(record);
      positions.add(record.getPosition());
    }

    // then
    assertThat(memoryExporter.getFinishedLogRecordItems()).hasSize(10);
    assertThat(controller.getPosition())
        .isEqualTo(positions.stream().mapToLong(Long::longValue).max().orElseThrow());
  }

  @Test
  void shouldContinueWhenHandlerThrows() {
    // given
    final var failController = new ExporterTestController();
    final var failExporter = exporterWithThrowingOtel(failController);
    final var record = piCreatedEvent();

    // when / then — export must not propagate the exception
    assertThatCode(() -> failExporter.export(record)).doesNotThrowAnyException();
    assertThat(failController.getPosition()).isEqualTo(record.getPosition());
  }

  @Test
  void shouldUpdatePositionForAllRecords() {
    // given
    final var record = FACTORY.generateRecord(ValueType.JOB);

    // when
    exporter.export(record);

    // then
    assertThat(controller.getPosition()).isEqualTo(record.getPosition());
    assertThat(memoryExporter.getFinishedLogRecordItems()).isEmpty();
  }

  @Test
  void shouldNotSerializeMetadataWhenHandlerNoOps() {
    // given — a record that passes the AnalyticsRecordFilter but the handler skips because the
    // element isn't an ad-hoc sub-process. This is the realistic hot-path no-op case.
    final var value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnElementType(BpmnElementType.SERVICE_TASK)
            .build();
    final var record =
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                    .withValue(value));

    // when
    exporter.export(record);

    // then — position advances but no metadata was written, because the handler didn't touch
    // it so serializing again would be wasted work
    assertThat(controller.getPosition()).isEqualTo(record.getPosition());
    assertThat(controller.readMetadata()).isEmpty();
  }

  @Test
  void shouldPersistMetadataWhenHandlerThrowsAfterMutation() {
    // given — OtelSdkManager that increments the sequence number and then throws
    final var throwingController = new ExporterTestController();
    final var throwingExporter = exporterWithMutatingThrowingOtel(throwingController);
    final var record = piCreatedEvent();

    // when — export must not propagate the exception
    assertThatCode(() -> throwingExporter.export(record)).doesNotThrowAnyException();

    // then — metadata was persisted despite the exception, because the mutation happened before
    // throw
    assertThat(throwingController.readMetadata())
        .isPresent()
        .get()
        .satisfies(
            bytes -> {
              final var persisted = AnalyticsExporterMetadata.deserialize(bytes);
              assertThat(persisted.getEventSequenceNumber()).isEqualTo(1L);
            });
  }

  @Test
  void shouldPersistMetadataMutatedOutsideHandler() {
    // given — a custom OtelSdkManager that exposes the metadata so the test can simulate a gauge
    // flush callback (which bumps the metric sequence number outside the export path)
    final var metricOnlyController = new ExporterTestController();
    final var metadataHolder = new AnalyticsExporterMetadata[1];
    final var metricOnlyExporter =
        newExporter(
            new OtelSdkManager() {
              @Override
              OtelSdkManager initialize(
                  final AnalyticsExporterConfig cfg,
                  final AnalyticsExporterContext ctx,
                  final AnalyticsExporterMetadata meta,
                  final io.micrometer.core.instrument.MeterRegistry registry) {
                metadataHolder[0] = meta;
                return this;
              }

              @Override
              public void logEvent(
                  final String eventName,
                  final long logPosition,
                  final Consumer<LogRecordBuilder> builder) {
                // no-op — event logging is not under test here
              }
            },
            metricOnlyController);

    // Simulate the gauge flush callback bumping the metric sequence number outside the export path.
    metadataHolder[0].incrementAndGetMetricSequenceNumber();

    // when — export an unhandled record (no handler runs, but metadata is already dirty)
    final var unhandledRecord = FACTORY.generateRecord(ValueType.JOB);
    metricOnlyExporter.export(unhandledRecord);

    // then — metadata was persisted because the metric seqnum was bumped outside the export path
    assertThat(metricOnlyController.readMetadata())
        .isPresent()
        .get()
        .satisfies(
            bytes -> {
              final var persisted = AnalyticsExporterMetadata.deserialize(bytes);
              assertThat(persisted.getMetricSequenceNumber()).isEqualTo(1L);
            });
  }

  @Test
  void shouldInstallRecordFilterOnConfigure() {
    // given
    final var context =
        new ExporterTestContext()
            .setConfiguration(
                new ExporterTestConfiguration<>("analytics", new AnalyticsExporterConfig()))
            .setClusterId("test-cluster")
            .setPartitionId(1)
            .setLicenseKey("test-license-key");

    // when
    new AnalyticsExporter().configure(context);

    // then
    assertThat(context.getRecordFilter()).isNotNull().isInstanceOf(AnalyticsRecordFilter.class);
  }

  @Test
  void shouldPersistSequenceNumberInMetadata() {
    // given / when — use explicit increasing positions so the controller always persists metadata
    exporter.export(piCreatedEvent(1L));
    exporter.export(piCreatedEvent(2L));
    exporter.export(piCreatedEvent(3L));

    // then
    assertThat(controller.readMetadata())
        .isPresent()
        .get()
        .satisfies(
            bytes -> {
              final var metadata = AnalyticsExporterMetadata.deserialize(bytes);
              assertThat(metadata.getEventSequenceNumber()).isEqualTo(3L);
              // metricSequenceNumber is only incremented during metric flush (gauge callback),
              // not during export(). Since no metric flush occurs here, it stays at 0.
              assertThat(metadata.getMetricSequenceNumber()).isZero();
            });
  }

  @Test
  void shouldRestoreSequenceNumberFromMetadataOnOpen() {
    // given — controller pre-seeded with persisted sequence number 5
    final var seededController = new ExporterTestController();
    seededController.updateLastExportedRecordPosition(
        0L, new AnalyticsExporterMetadata(5L, 0).serialize());
    final var freshMemoryExporter = InMemoryLogRecordExporter.create();
    final var freshExporter = exporterWithInMemory(freshMemoryExporter, seededController);

    // when
    freshExporter.export(piCreatedEvent());

    // then
    assertThat(freshMemoryExporter.getFinishedLogRecordItems())
        .singleElement()
        .extracting(log -> log.getAttributes().get(AnalyticsAttributes.Event.SEQUENCE_NUMBER))
        .isEqualTo(6L);
  }

  @Test
  void shouldEmitUserTaskCreatedEvent() {
    // given
    final var record =
        FACTORY.generateRecord(
            ValueType.USER_TASK,
            r -> r.withRecordType(RecordType.EVENT).withIntent(UserTaskIntent.CREATED));

    // when
    exporter.export(record);

    // then
    assertThat(memoryExporter.getFinishedLogRecordItems())
        .singleElement()
        .satisfies(
            logRecord -> {
              assertThat(logRecord.getAttributes().get(AnalyticsAttributes.Event.NAME))
                  .isEqualTo(AnalyticsAttributes.Event.USER_TASK_CREATED);
              assertThat(logRecord.getAttributes().get(AnalyticsAttributes.Log.POSITION))
                  .isEqualTo(record.getPosition());
            });
  }

  @Test
  void shouldNotThrowOnCloseWhenNeverOpened() {
    // given — exporter is configured but open() was never called, so controller is null
    final var context =
        new ExporterTestContext()
            .setConfiguration(
                new ExporterTestConfiguration<>("analytics", new AnalyticsExporterConfig()))
            .setClusterId("test-cluster")
            .setPartitionId(1)
            .setLicenseKey("test-license-key");
    final var unopenedExporter = new AnalyticsExporter();
    unopenedExporter.configure(context);

    // when / then — close must not throw even though controller is null
    assertThatCode(unopenedExporter::close).doesNotThrowAnyException();
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
    return newExporter(TestOtelSdkManager.inMemory(memoryExporter), controller);
  }

  private static AnalyticsExporter exporterWithThrowingOtel(
      final ExporterTestController controller) {
    return newExporter(
        new OtelSdkManager() {
          @Override
          public void logEvent(
              final String eventName,
              final long logPosition,
              final Consumer<LogRecordBuilder> builder) {
            throw new RuntimeException("simulated failure");
          }
        },
        controller);
  }

  /**
   * Builds an exporter whose {@code OtelSdkManager.logEvent} increments the event sequence number
   * (via the metadata reference captured during {@code initialize}) and then throws. This simulates
   * a handler that mutates metadata and then fails mid-way.
   */
  private static AnalyticsExporter exporterWithMutatingThrowingOtel(
      final ExporterTestController controller) {
    final var metadataHolder = new AnalyticsExporterMetadata[1];
    return newExporter(
        new OtelSdkManager() {
          @Override
          OtelSdkManager initialize(
              final AnalyticsExporterConfig cfg,
              final AnalyticsExporterContext ctx,
              final AnalyticsExporterMetadata meta,
              final io.micrometer.core.instrument.MeterRegistry registry) {
            metadataHolder[0] = meta;
            return this;
          }

          @Override
          public void logEvent(
              final String eventName,
              final long logPosition,
              final Consumer<LogRecordBuilder> builder) {
            metadataHolder[0].incrementAndGetEventSequenceNumber();
            throw new RuntimeException("simulated failure after mutation");
          }
        },
        controller);
  }

  private static AnalyticsExporter newExporter(
      final OtelSdkManager otelSdkManager, final ExporterTestController controller) {
    final var context =
        new ExporterTestContext()
            .setConfiguration(
                new ExporterTestConfiguration<>("analytics", new AnalyticsExporterConfig()))
            .setClusterId("test-cluster")
            .setPartitionId(1)
            .setLicenseKey("test-license-key");
    final var exporter = new AnalyticsExporter(otelSdkManager);
    exporter.configure(context);
    exporter.open(controller);
    return exporter;
  }
}
