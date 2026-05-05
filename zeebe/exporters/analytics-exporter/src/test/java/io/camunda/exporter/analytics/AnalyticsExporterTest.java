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
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnalyticsExporterTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  @Nested
  class Configuration {

    @Test
    void shouldRejectMissingEndpoint() {
      // given
      final var context =
          new ExporterTestContext()
              .setConfiguration(
                  new ExporterTestConfiguration<>(
                      "analytics", new AnalyticsExporterConfig().setEnabled(true).setEndpoint("")));

      // when / then
      assertThatThrownBy(() -> new AnalyticsExporter().configure(context))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptMissingEndpointWhenDisabled() {
      // given
      final var context =
          new ExporterTestContext()
              .setConfiguration(
                  new ExporterTestConfiguration<>(
                      "analytics", new AnalyticsExporterConfig().setEndpoint("")));

      // when / then
      assertThatCode(() -> new AnalyticsExporter().configure(context)).doesNotThrowAnyException();
    }
  }

  @Nested
  class ExportBehavior {

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
                    .containsEntry(AnalyticsAttributes.EVENT_NAME, "process_instance_created")
                    .containsEntry(AnalyticsAttributes.BPMN_PROCESS_ID, value.getBpmnProcessId())
                    .containsEntry(AnalyticsAttributes.PROCESS_VERSION, (long) value.getVersion())
                    .containsEntry(
                        AnalyticsAttributes.PROCESS_DEFINITION_KEY, value.getProcessDefinitionKey())
                    .containsEntry(AnalyticsAttributes.PROCESS_INSTANCE_KEY, record.getKey())
                    .containsEntry(
                        AnalyticsAttributes.ROOT_PROCESS_INSTANCE_KEY,
                        value.getRootProcessInstanceKey())
                    .containsEntry(AnalyticsAttributes.TENANT_ID, value.getTenantId())
                    .containsEntry(AnalyticsAttributes.LOG_POSITION, record.getPosition());
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
    void shouldUpdatePositionWhenDisabled() {
      // given
      final var disabledConfig = new AnalyticsExporterConfig();
      final var disabledController = new ExporterTestController();
      final var context =
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("analytics", disabledConfig));
      final var disabledExporter = new AnalyticsExporter();
      disabledExporter.configure(context);
      disabledExporter.open(disabledController);

      final var record = FACTORY.generateRecord(ValueType.JOB);

      // when
      disabledExporter.export(record);

      // then
      assertThat(disabledController.getPosition()).isEqualTo(record.getPosition());
    }
  }

  // -- helpers --

  private static io.camunda.zeebe.protocol.record.Record<?> piCreatedEvent() {
    return FACTORY.generateRecord(
        ValueType.PROCESS_INSTANCE_CREATION,
        r -> r.withRecordType(RecordType.EVENT).withIntent(ProcessInstanceCreationIntent.CREATED));
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

  private static AnalyticsExporter exporterWithThrowingOtel(
      final ExporterTestController controller) {
    return newExporter(
        new OtelSdkManager() {
          @Override
          void logEvent(
              final String eventName,
              final long logPosition,
              final Consumer<LogRecordBuilder> builder) {
            throw new RuntimeException("simulated failure");
          }
        },
        controller);
  }

  private static AnalyticsExporter newExporter(
      final OtelSdkManager otelSdkManager, final ExporterTestController controller) {
    final var context =
        new ExporterTestContext()
            .setConfiguration(
                new ExporterTestConfiguration<>(
                    "analytics", new AnalyticsExporterConfig().setEnabled(true)))
            .setClusterId("test-cluster")
            .setPartitionId(1);
    final var exporter = new AnalyticsExporter(otelSdkManager);
    exporter.configure(context);
    exporter.open(controller);
    return exporter;
  }
}
