/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.otlp.internal.HttpExporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An unreachable telemetry endpoint is an expected condition. The OTel SDK reports it via
 * java.util.logging at SEVERE with a stack trace, which surfaces as an ERROR in Camunda's logs.
 *
 * <p>Records are logged straight to the SDK's logger, as {@code HttpExporter} does, and observed
 * after the installed filter has run.
 */
class OtelExportFailureLoggingTest {

  private static final String SDK_MESSAGE =
      "Failed to export logs. The request could not be executed.";

  // The SDK logger is shared by the whole JVM; the marker keeps this test from seeing records
  // logged by SDK instances that other tests leave running.
  private final String marker = UUID.randomUUID().toString();
  private final String sdkMessage = SDK_MESSAGE + " [" + marker + "]";
  private final Logger sdkLogger = Logger.getLogger(HttpExporter.class.getName());
  private final List<LogRecord> records = new CopyOnWriteArrayList<>();
  private final Handler captureHandler =
      new Handler() {
        @Override
        public void publish(final LogRecord record) {
          if (record.getMessage() != null && record.getMessage().contains(marker)) {
            records.add(record);
          }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
      };

  @BeforeEach
  void setUp() {
    ExportFailureLogFilter.install();
    sdkLogger.addHandler(captureHandler);
  }

  @AfterEach
  void tearDown() {
    sdkLogger.removeHandler(captureHandler);
  }

  @Test
  void shouldLogUnresolvedEndpointAsWarningWithRootCauseOnly() {
    // given
    final var connectException = new ConnectException();
    connectException.initCause(new UnresolvedAddressException());
    final var failure = new CompletionException(new UncheckedIOException(connectException));

    // when
    sdkLogger.log(Level.SEVERE, sdkMessage, failure);

    // then
    assertThat(records)
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.getLevel()).isEqualTo(Level.WARNING);
              assertThat(record.getThrown()).isNull();
              assertThat(record.getMessage())
                  .startsWith(sdkMessage)
                  .endsWith("Cause: java.nio.channels.UnresolvedAddressException");
            });
  }

  @Test
  void shouldStopFollowingCausesAtMaximumDepth() {
    // given
    Throwable chain = null;
    for (int i = ExportFailureLogFilter.MAX_CAUSE_DEPTH + 10; i >= 0; i--) {
      chain = new IOException("cause-" + i, chain);
    }

    // when
    sdkLogger.log(Level.SEVERE, sdkMessage, chain);

    // then
    assertThat(records)
        .singleElement()
        .satisfies(
            record ->
                assertThat(record.getMessage())
                    .endsWith(
                        "Cause: java.io.IOException: cause-"
                            + ExportFailureLogFilter.MAX_CAUSE_DEPTH));
  }

  @Test
  void shouldLogConnectTimeoutAsWarningWithRootCauseOnly() {
    // given
    final var failure =
        new CompletionException(
            new UncheckedIOException(new HttpConnectTimeoutException("HTTP connect timed out")));

    // when
    sdkLogger.log(Level.SEVERE, sdkMessage, failure);

    // then
    assertThat(records)
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.getLevel()).isEqualTo(Level.WARNING);
              assertThat(record.getThrown()).isNull();
              assertThat(record.getMessage())
                  .endsWith(
                      "Cause: java.net.http.HttpConnectTimeoutException: HTTP connect timed out");
            });
  }

  @Test
  void shouldDowngradeSevereWithoutThrowableAndKeepMessage() {
    // when
    sdkLogger.log(Level.SEVERE, sdkMessage);

    // then
    assertThat(records)
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.getLevel()).isEqualTo(Level.WARNING);
              assertThat(record.getMessage()).isEqualTo(sdkMessage);
            });
  }

  @Test
  void shouldKeepOtherSevereRecordsUntouched() {
    // given
    final var message =
        "Failed to export logs. The request body size could not be computed. [" + marker + "]";
    final var failure = new IOException("boom");

    // when
    sdkLogger.log(Level.SEVERE, message, failure);

    // then
    assertThat(records)
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.getLevel()).isEqualTo(Level.SEVERE);
              assertThat(record.getMessage()).isEqualTo(message);
              assertThat(record.getThrown()).isSameAs(failure);
            });
  }

  @Test
  void shouldLeaveNonSevereRecordsUntouched() {
    // given
    final var failure = new IOException("boom");

    // when
    sdkLogger.log(Level.WARNING, "something else " + marker, failure);

    // then
    assertThat(records)
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.getLevel()).isEqualTo(Level.WARNING);
              assertThat(record.getMessage()).isEqualTo("something else " + marker);
              assertThat(record.getThrown()).isSameAs(failure);
            });
  }
}
