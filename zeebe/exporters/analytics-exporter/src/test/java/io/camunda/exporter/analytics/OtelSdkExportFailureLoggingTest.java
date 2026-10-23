/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.exporter.otlp.internal.HttpExporter;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The filter matches the SDK's own wording, so a unit test with a hard-coded message cannot notice
 * an OTel upgrade that rewords it. This test goes through the real SDK instead, against a closed
 * local port.
 */
class OtelSdkExportFailureLoggingTest {

  private final Logger sdkLogger = Logger.getLogger(HttpExporter.class.getName());
  private final List<LogRecord> records = new CopyOnWriteArrayList<>();
  private final Handler captureHandler =
      new Handler() {
        @Override
        public void publish(final LogRecord record) {
          records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
      };

  private OtelSdkManager manager;

  @BeforeEach
  void setUp() {
    sdkLogger.addHandler(captureHandler);
  }

  @AfterEach
  void tearDown() {
    sdkLogger.removeHandler(captureHandler);
    if (manager != null) {
      manager.close();
    }
  }

  @Test
  @Timeout(30)
  void shouldLogRealSdkExportFailureAsWarningWithoutStackTrace() throws IOException {
    // given
    manager =
        new OtelSdkManager()
            .initialize(
                new AnalyticsExporterConfig()
                    .setEndpoint("http://localhost:" + closedPort())
                    .setPushInterval("PT0.2S")
                    .setHttpMaxRetryAttempts(2),
                AnalyticsExporterContext.create(
                    "test-license", "test-cluster", 1, "test-physical-tenant", ""),
                new AnalyticsExporterMetadata());

    // when
    manager.logEvent("test", 0L, log -> {});

    // then
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertThat(records)
                    .filteredOn(record -> record.getMessage().contains("could not be executed"))
                    .isNotEmpty()
                    .allSatisfy(
                        record -> {
                          assertThat(record.getLevel()).isEqualTo(Level.WARNING);
                          assertThat(record.getThrown()).isNull();
                          assertThat(record.getMessage()).contains("Cause:");
                        }));
  }

  private static int closedPort() throws IOException {
    try (final var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
