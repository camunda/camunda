/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter.stream;

import io.prometheus.client.Counter;
import io.zeebe.protocol.record.ValueType;

public final class ExporterMetrics {

  private static final Counter EXPORTER_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("exporter_events_total")
          .help("Number of events processed by exporter")
          .labelNames("action", "partition", "valueType")
          .register();

  private final String partitionIdLabel;

  public ExporterMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  private void event(final String action, final ValueType valueType) {
    EXPORTER_EVENTS.labels(action, partitionIdLabel, valueType.name()).inc();
  }

  public void eventExported(final ValueType valueType) {
    event("exported", valueType);
  }

  public void eventSkipped(final ValueType valueType) {
    event("skipped", valueType);
  }
}
