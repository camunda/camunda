/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine.impl;

import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** Defines metrics for scheduled command cache implementations. */
public interface ScheduledCommandCacheMetrics {

  /**
   * Returns a consumer for a given intent, which will be called whenever the underlying cache for
   * this intent changes size.
   */
  IntConsumer forIntent(final Intent intent);

  /**
   * A metrics implementation specifically for the {@link
   * io.camunda.zeebe.broker.engine.impl.BoundedScheduledCommandCache}.
   */
  class BoundedCommandCacheMetrics implements ScheduledCommandCacheMetrics {
    private final Map<Intent, AtomicInteger> sizes = new HashMap<>();
    private final MeterRegistry registry;

    public BoundedCommandCacheMetrics(final MeterRegistry registry) {
      this.registry = Objects.requireNonNull(registry, "must specify a meter registry");
    }

    @Override
    public IntConsumer forIntent(final Intent intent) {
      return sizes.computeIfAbsent(intent, this::registerSizeReporter)::set;
    }

    private AtomicInteger registerSizeReporter(final Intent intent) {
      final var intentLabelValue = intent.getClass().getSimpleName() + "." + intent.name();
      final var meterDoc = BoundedCacheMetricsDoc.SIZE;
      final var sizeTracker = new AtomicInteger();
      Gauge.builder(meterDoc.getName(), sizeTracker, AtomicInteger::intValue)
          .description(meterDoc.getDescription())
          .tag(SizeKeys.INTENT.asString(), intentLabelValue)
          .register(registry);

      return sizeTracker;
    }
  }

  /**
   * Documentation for the metrics used by {@link BoundedCommandCache}. See {@link
   * BoundedCommandCacheMetrics} for more.
   */
  @SuppressWarnings("NullableProblems")
  enum BoundedCacheMetricsDoc implements ExtendedMeterDocumentation {
    /** Reports the size of each bounded cache per partition and intent */
    SIZE {
      @Override
      public String getName() {
        return "zeebe.stream.processor.scheduled.command.cache.size";
      }

      @Override
      public Meter.Type getType() {
        return Meter.Type.GAUGE;
      }

      @Override
      public String getDescription() {
        return "Reports the size of each bounded cache per partition and intent";
      }

      @Override
      public KeyName[] getKeyNames() {
        return SizeKeys.values();
      }

      @Override
      public KeyName[] getAdditionalKeyNames() {
        return PartitionKeyNames.values();
      }
    },
  }

  @SuppressWarnings("NullableProblems")
  enum SizeKeys implements KeyName {
    /** The specific command intent for this cached keyy */
    INTENT {
      @Override
      public String asString() {
        return "intent";
      }
    }
  }
}
