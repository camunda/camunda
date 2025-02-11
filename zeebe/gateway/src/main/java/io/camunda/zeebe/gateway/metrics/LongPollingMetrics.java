/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.metrics;

import static io.camunda.zeebe.gateway.metrics.LongPollingMetrics.LongPollingMetricsDoc.REQUESTS_QUEUED_CURRENT;
import static io.camunda.zeebe.gateway.metrics.LongPollingMetrics.RequestsQueuedKeyNames.TYPE;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class LongPollingMetrics {
  private final Map<String, AtomicLong> requestsQueued = new HashMap<>();
  private final MeterRegistry registry;

  public LongPollingMetrics(final MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "must specify a meter registry");
  }

  public void setBlockedRequestsCount(final String type, final int count) {
    requestsQueued.computeIfAbsent(type, this::registerBlockedRequestsCount).set(count);
  }

  private AtomicLong registerBlockedRequestsCount(final String type) {
    final var count = new AtomicLong();
    Gauge.builder(REQUESTS_QUEUED_CURRENT.getName(), count, Number::longValue)
        .description(REQUESTS_QUEUED_CURRENT.getDescription())
        .tag(TYPE.asString(), type)
        .register(registry);

    return count;
  }

  /** Number of requests currently queued due to long polling */
  @SuppressWarnings("NullableProblems")
  public enum LongPollingMetricsDoc implements ExtendedMeterDocumentation {
    REQUESTS_QUEUED_CURRENT {
      @Override
      public String getDescription() {
        return "Number of requests currently queued due to long polling";
      }

      @Override
      public String getName() {
        return "zeebe.long.polling.queued.current";
      }

      @Override
      public Type getType() {
        return Type.GAUGE;
      }

      @Override
      public KeyName[] getKeyNames() {
        return RequestsQueuedKeyNames.values();
      }
    }
  }

  @SuppressWarnings("NullableProblems")
  public enum RequestsQueuedKeyNames implements KeyName {
    /** The job type associated with the blocked request */
    TYPE {
      @Override
      public String asString() {
        return "type";
      }
    }
  }
}
