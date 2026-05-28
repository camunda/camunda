/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;

/** Test factory for {@link OtelSdkManager} with in-memory OTel transport. */
public final class TestOtelSdkManager {

  private TestOtelSdkManager() {}

  /** Creates an initialized OtelSdkManager that captures events in the given exporter. */
  public static OtelSdkManager inMemory(final InMemoryLogRecordExporter memoryExporter) {
    final var manager =
        new OtelSdkManager() {
          @Override
          protected SdkLoggerProvider createLoggerProvider(
              final AnalyticsExporterConfig cfg, final AnalyticsExporterContext context) {
            return SdkLoggerProvider.builder()
                .setResource(OtelSdkManager.buildResource(context))
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(memoryExporter))
                .build();
          }
        };
    manager.initialize(
        new AnalyticsExporterConfig(),
        AnalyticsExporterContext.create("test-license", "test-cluster", 1),
        new AnalyticsExporterMetadata());
    return manager;
  }
}
