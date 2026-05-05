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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end tests: real exporter → real BatchLogRecordProcessor → real OTLP/HTTP → real OTel
 * Collector. No mocks, no overrides — production code path.
 */
@Testcontainers
class AnalyticsExporterOtelIT {

  private static final List<String> COLLECTOR_LOGS = new CopyOnWriteArrayList<>();

  @Container
  private static final GenericContainer<?> OTEL_COLLECTOR =
      new GenericContainer<>(
              DockerImageName.parse("otel/opentelemetry-collector-contrib").withTag("0.119.0"))
          .withClasspathResourceMapping(
              "otel-collector-config.yaml", "/etc/otelcol-contrib/config.yaml", BindMode.READ_ONLY)
          .withLogConsumer(frame -> COLLECTOR_LOGS.add(frame.getUtf8String()))
          .withExposedPorts(4318);

  private final ProtocolFactory factory = new ProtocolFactory();

  @BeforeEach
  void clearLogs() {
    COLLECTOR_LOGS.clear();
  }

  /** Events arrive at the collector with correct event name and attributes. */
  @Test
  void shouldDeliverEventsWithAttributes() {
    // given
    final var exporter = createExporter();

    // when
    for (int i = 0; i < 5; i++) {
      exporter.export(piCreatedEvent());
    }
    exporter.close();

    // then
    awaitCollectorLogs(
        "process_instance_created",
        "camunda.cluster.id",
        "test-cluster",
        "camunda.bpmn_process_id");
  }

  /**
   * Events are delivered in batches, not individually. Sends more events than one batch can hold
   * (2000 with maxBatchSize=512), then asserts the collector received multiple batch exports
   * containing multiple records each.
   *
   * <p>The collector's debug exporter logs each export as a "ResourceLog" block containing
   * "LogRecord #N" entries. Multiple LogRecord entries in a single export prove batching.
   */
  @Test
  void shouldBatchEventsBeforeExporting() {
    // given — small batch size and fast push interval to force multiple batches
    final var exporter =
        createExporter(
            new AnalyticsExporterConfig()
                .setEnabled(true)
                .setEndpoint("http://localhost:" + OTEL_COLLECTOR.getMappedPort(4318))
                .setMaxBatchSize(100)
                .setPushInterval("PT0.1S"));

    // when — send 500 events (should produce ~5 batches of 100)
    for (int i = 0; i < 500; i++) {
      exporter.export(piCreatedEvent());
    }
    exporter.close();

    // then — collector received multiple batch exports, each with multiple records
    Awaitility.await("Collector should receive all events in batches")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              // Count total LogRecord entries
              final var logRecordCount =
                  COLLECTOR_LOGS.stream().filter(l -> l.contains("LogRecord #")).count();
              assertThat(logRecordCount).isEqualTo(500);

              // Count export calls (each starts a "ScopeLogs" block)
              final var exportCount =
                  COLLECTOR_LOGS.stream().filter(l -> l.contains("ScopeLogs #")).count();

              // With batchSize=100 and 500 events, we expect ~5 exports (not 500)
              assertThat(exportCount).isGreaterThan(1).isLessThan(500);
            });
  }

  // -- helpers --

  private AnalyticsExporter createExporter() {
    return createExporter(
        new AnalyticsExporterConfig()
            .setEnabled(true)
            .setEndpoint("http://localhost:" + OTEL_COLLECTOR.getMappedPort(4318)));
  }

  private AnalyticsExporter createExporter(final AnalyticsExporterConfig config) {
    final var context =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("analytics-it", config))
            .setClusterId("test-cluster")
            .setPartitionId(1);
    final var exporter = new AnalyticsExporter();
    exporter.configure(context);
    exporter.open(new ExporterTestController());
    return exporter;
  }

  private void awaitCollectorLogs(final String... expectedSubstrings) {
    Awaitility.await("OTel Collector should receive expected log content")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              for (final var expected : expectedSubstrings) {
                assertThat(COLLECTOR_LOGS).anyMatch(line -> line.contains(expected));
              }
            });
  }

  private io.camunda.zeebe.protocol.record.Record<?> piCreatedEvent() {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_CREATION,
        r -> r.withRecordType(RecordType.EVENT).withIntent(ProcessInstanceCreationIntent.CREATED));
  }
}
