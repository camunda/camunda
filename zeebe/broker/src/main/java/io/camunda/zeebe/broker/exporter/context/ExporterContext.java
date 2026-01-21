/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.EnsureUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.time.InstantSource;
import org.slf4j.Logger;

public final class ExporterContext implements Context, AutoCloseable {

  private static final RecordFilter DEFAULT_FILTER = new AcceptAllRecordsFilter();
  private final Logger logger;
  private final Configuration configuration;
  private final int partitionId;
  private final String engineName;
  private final CompositeMeterRegistry meterRegistry;
  private final InstantSource clock;
  private RecordFilter filter = DEFAULT_FILTER;

  public ExporterContext(
      final Logger logger,
      final Configuration configuration,
      final int partitionId,
      final String engineName,
      final MeterRegistry meterRegistry,
      final InstantSource clock) {
    this.logger = logger;
    this.configuration = configuration;
    this.partitionId = partitionId;
    this.meterRegistry =
        MicrometerUtil.wrap(
            meterRegistry,
            Tags.concat(
                PartitionKeyNames.tags(partitionId), Tags.of("exporterId", configuration.getId())));
    this.clock = clock;
    this.engineName = engineName;
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  @Override
  public Logger getLogger() {
    return logger;
  }

  @Override
  public InstantSource clock() {
    return clock;
  }

  @Override
  public Configuration getConfiguration() {
    return configuration;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public String getEngineName() {
    return engineName;
  }

  public RecordFilter getFilter() {
    return filter;
  }

  @Override
  public void setFilter(final RecordFilter filter) {
    EnsureUtil.ensureNotNull("filter", filter);
    this.filter = filter;
  }

  @Override
  public void close() {
    MicrometerUtil.close(meterRegistry);
  }

  private static final class AcceptAllRecordsFilter implements RecordFilter {

    @Override
    public boolean acceptType(final RecordType recordType) {
      return true;
    }

    @Override
    public boolean acceptValue(final ValueType valueType) {
      return true;
    }
  }
}
