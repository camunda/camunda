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
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
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

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ExporterTestClock clock = new ExporterTestClock();

  private Configuration configuration;
  private RecordFilter recordFilter;
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

  public void pinClock(final long epochMillis) {
    clock.pinnedTime.set(epochMillis);
  }

  public static final class ExporterTestClock implements InstantSource {
    private final AtomicLong pinnedTime = new AtomicLong(-1);

    @Override
    public Instant instant() {
      final var pinnedMillis = pinnedTime.get();
      if (pinnedMillis < 0) {
        return Instant.now();
      }

      return Instant.ofEpochMilli(pinnedMillis);
    }
  }
}
