/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import java.util.List;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that detects leaked {@code CamundaExporter} flush executor threads after each
 * test. The {@code CamundaExporter.open()} method creates a single-thread executor with a thread
 * named {@code "camunda-exporter-flush-partition-<partitionId>"}. If a test opens an exporter but
 * fails to close it, this executor thread will leak.
 *
 * <p>Register this extension on any test class that creates and opens {@code CamundaExporter}
 * instances to ensure all exporters are properly closed.
 */
public class ExporterThreadLeakExtension implements AfterEachCallback {

  private static final String FLUSH_THREAD_PREFIX = "camunda-exporter-flush-partition-";

  @Override
  public void afterEach(final ExtensionContext context) {
    final List<String> leakedThreadNames =
        Thread.getAllStackTraces().keySet().stream()
            .filter(Thread::isAlive)
            .map(Thread::getName)
            .filter(name -> name.startsWith(FLUSH_THREAD_PREFIX))
            .sorted()
            .toList();

    if (!leakedThreadNames.isEmpty()) {
      throw new AssertionError(
          String.format(
              "Detected leaked exporter flush thread(s) after test '%s': %s. "
                  + "Ensure all CamundaExporter instances are closed via close() after use.",
              context.getDisplayName(), leakedThreadNames));
    }
  }
}
