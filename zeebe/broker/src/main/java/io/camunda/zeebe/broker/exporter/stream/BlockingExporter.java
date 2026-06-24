/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a special Exporter which is configured in place of previously enabled exporters whose
 * configuration is not found. The exporting to this exporter will be paused until the configuration
 * is fixed and the broker is restarted. Or the exporter is permanently deleted via explicit call to
 * the management api.
 */
public class BlockingExporter implements Exporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(BlockingExporter.class);
  private String exporterId;

  @Override
  public void configure(final Context context) throws Exception {
    exporterId = context.getConfiguration().getId();
    LOGGER.warn(
        "Exporter '{}' is enabled, but the configuration is not found. Exporting to this exporter will be paused."
            + " Please add the exporter configuration or delete them permanently via management api.",
        exporterId);
  }

  @Override
  public void open(final Controller controller) {
    // No op
  }

  @Override
  public void close() {
    Exporter.super.close();
  }

  @Override
  public void export(final Record<?> record) {
    // no-op
    // This exporter never updates Controller#updateLastExportedRecordPosition. Thus, the partition
    // will
    // never compact the logs.
  }
}
