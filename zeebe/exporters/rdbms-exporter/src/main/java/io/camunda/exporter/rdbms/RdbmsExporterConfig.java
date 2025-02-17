/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record RdbmsExporterConfig(
    int partitionId,
    Duration flushInterval,
    int maxQueueSize,
    RdbmsWriter rdbmsWriter,
    Map<ValueType, List<RdbmsExportHandler>> handlers) {

  public static RdbmsExporterConfig of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  // create a static builder for this record. Also create methods to add handlers to the map
  public static final class Builder {

    private int partitionId;
    private Duration flushInterval;
    private int maxQueueSize;
    private RdbmsWriter rdbmsWriter;
    private Map<ValueType, List<RdbmsExportHandler>> handlers = new HashMap<>();

    public Builder partitionId(final int value) {
      partitionId = value;
      return this;
    }

    public Builder flushInterval(final Duration value) {
      flushInterval = value;
      return this;
    }

    public Builder maxQueueSize(final int value) {
      maxQueueSize = value;
      return this;
    }

    public Builder rdbmsWriter(final RdbmsWriter value) {
      rdbmsWriter = value;
      return this;
    }

    public Builder handlers(final Map<ValueType, List<RdbmsExportHandler>> value) {
      handlers = value;
      return this;
    }

    public Builder withHandler(final ValueType valueType, final RdbmsExportHandler handler) {
      if (!handlers.containsKey(valueType)) {
        handlers.put(valueType, new ArrayList<>());
      }
      handlers.get(valueType).add(handler);

      return this;
    }

    public RdbmsExporterConfig build() {
      return new RdbmsExporterConfig(
          partitionId, flushInterval, maxQueueSize, rdbmsWriter, handlers);
    }
  }
}
