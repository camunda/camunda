/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.opentelemetry.exporter.otlp.internal.HttpExporter;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Logs OTel SDK export requests that could not be executed at WARNING with only the root cause,
 * instead of SEVERE with a stack trace.
 *
 * <p>An unreachable telemetry endpoint is expected and harmless to the broker: the events are
 * dropped after retries, so it should not appear as an ERROR. Other SEVERE records, such as a
 * failure to compute the request body size, are left untouched because they point to a defect in
 * the exporter that someone needs to see with its stack trace.
 *
 * <p>How it works: the SDK reports failures through a {@code java.util.logging} logger named after
 * its {@code HttpExporter} class. {@link #install()} attaches this filter to that logger, and every
 * record the SDK logs passes through {@link #isLoggable} first, where it is rewritten before any
 * log handler sees it.
 */
final class ExportFailureLogFilter implements Filter {

  static final int MAX_CAUSE_DEPTH = 20;

  // Wording used by the SDK when a request fails before any response, e.g. DNS or connect errors
  private static final String REQUEST_NOT_EXECUTED = "The request could not be executed";

  // Kept in a static field because java.util.logging holds loggers weakly: without a strong
  // reference the logger, and the filter on it, could be garbage collected before the SDK first
  // uses it. The SDK would then get a fresh logger without the filter.
  private static final Logger SDK_LOGGER = Logger.getLogger(HttpExporter.class.getName());
  private static final ExportFailureLogFilter INSTANCE = new ExportFailureLogFilter();

  private ExportFailureLogFilter() {}

  /** Idempotent; the filter is process-wide because the SDK logger is. */
  static void install() {
    if (SDK_LOGGER.getFilter() != INSTANCE) {
      SDK_LOGGER.setFilter(INSTANCE);
    }
  }

  @Override
  public boolean isLoggable(final LogRecord record) {
    if (record.getLevel() != Level.SEVERE || !isRequestNotExecuted(record)) {
      return true;
    }
    record.setLevel(Level.WARNING);
    final var thrown = record.getThrown();
    if (thrown != null) {
      var root = thrown;
      // bounded so that a cyclic cause chain cannot hang the logging thread
      for (int depth = 0; depth < MAX_CAUSE_DEPTH && root.getCause() != null; depth++) {
        root = root.getCause();
      }
      record.setMessage(record.getMessage() + " Cause: " + root);
      record.setThrown(null);
    }
    return true;
  }

  private static boolean isRequestNotExecuted(final LogRecord record) {
    final var message = record.getMessage();
    return message != null && message.contains(REQUEST_NOT_EXECUTED);
  }
}
