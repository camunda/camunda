/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.test;

import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.Context;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.InstantSource;
import java.util.Objects;
import net.jcip.annotations.NotThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mutable implementation of {@link Context} for testing. The context is passed only during the
 * configuration phase, and any modifications afterwards isn't really used, so there is no real need
 * to make this thread-safe at the moment.
 */
@NotThreadSafe
public final class ExporterTestContext implements Context {
  private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(ExporterTestContext.class);

  private Configuration configuration;
  private RecordFilter recordFilter;
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private int partitionId;

  @Override
  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  @Override
  public Logger getLogger() {
    return DEFAULT_LOGGER;
  }

  @Override
  public InstantSource clock() {
    return InstantSource.system();
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
  public void setFilter(final RecordFilter filter) {
    recordFilter = filter;
  }

  public ExporterTestContext setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public ExporterTestContext setConfiguration(final Configuration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "must specify a configuration");
    return this;
  }

  public RecordFilter getRecordFilter() {
    return recordFilter;
  }
}
