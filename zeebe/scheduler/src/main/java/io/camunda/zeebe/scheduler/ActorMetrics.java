/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.util.CloseableSilently;
import io.micrometer.core.instrument.MeterRegistry;

public interface ActorMetrics {

  boolean isEnabled();

  ActorMetricsScoped scoped(final String actorName);

  void observeJobSchedulingLatency(final long waitTimeNs, final SubscriptionType subscriptionType);

  static ActorMetrics ofNullable(final MeterRegistry registry) {
    if (registry == null) {
      return disabled();
    } else {
      return new ActorMetricsImpl(registry);
    }
  }

  static ActorMetrics disabled() {
    return new ActorMetrics() {
      @Override
      public boolean isEnabled() {
        return false;
      }

      @Override
      public ActorMetricsScoped scoped(final String actorName) {
        return ActorMetricsScoped.noop();
      }

      @Override
      public void observeJobSchedulingLatency(
          final long waitTimeNs, final SubscriptionType subscriptionType) {}
    };
  }

  interface ActorMetricsScoped extends CloseableSilently {
    ActorMetricsScoped NOOP =
        new ActorMetricsScoped() {

          @Override
          public void close() {}

          @Override
          public void countExecution() {}

          @Override
          public void updateJobQueueLength(final int length) {}

          @Override
          public CloseableSilently startExecutionTimer() {
            return () -> {};
          }
        };

    void countExecution();

    void updateJobQueueLength(final int length);

    CloseableSilently startExecutionTimer();

    static ActorMetricsScoped noop() {
      return NOOP;
    }
  }

  enum SubscriptionType {
    FUTURE("Future"),
    TIMER("Timer"),
    NONE("None");

    private final String name;

    SubscriptionType(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }
}
