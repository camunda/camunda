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
  private final CompositeMeterRegistry meterRegistry;
  private final MeterRegistry underlyingMetricRegistry;
  private final InstantSource clock;

  private RecordFilter filter = DEFAULT_FILTER;

  public ExporterContext(
      final Logger logger,
      final Configuration configuration,
      final int partitionId,
      final MeterRegistry meterRegistry,
      final InstantSource clock) {
    this.logger = logger;
    this.configuration = configuration;
    this.partitionId = partitionId;
    underlyingMetricRegistry = meterRegistry;
    this.meterRegistry = new CompositeMeterRegistry();
    // meterRegistry is null in tests
    if (meterRegistry != null) {
      this.meterRegistry.add(meterRegistry);
    }
    this.meterRegistry
        .config()
        .commonTags(
            Tags.of(
                "partition", Integer.toString(partitionId),
                "exporterId", configuration.getId()));
    this.clock = clock;
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
    // Clear the registry, so the metrics created are deleted:
    // it must be done before removing the parent registry
    meterRegistry.clear();
    // remove the parentMeterRegistry so it's not closed when we close the composite.
    if (underlyingMetricRegistry != null) {
      meterRegistry.remove(underlyingMetricRegistry);
    }
    meterRegistry.close();
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
