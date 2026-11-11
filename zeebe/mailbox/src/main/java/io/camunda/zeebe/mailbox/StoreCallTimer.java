/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Times the calls to a store, by operation and outcome. In a multi-region setup most calls cross
 * regions to the database, so their latency is what the requests and the leases pay.
 *
 * <p>Tags: {@code operation} is the method of the store; {@code outcome} is {@code success}, or
 * {@code failure} if the call threw.
 */
public final class StoreCallTimer {

  private static final String SUCCESS = "success";
  private static final String FAILURE = "failure";

  private final MeterRegistry registry;
  private final String name;
  private final String description;
  private final Map<String, Timer> timers = new ConcurrentHashMap<>();

  public StoreCallTimer(final MeterRegistry registry, final String name, final String description) {
    this.registry = registry;
    this.name = name;
    this.description = description;
  }

  public <T> T time(final String operation, final Supplier<T> call) {
    final var sample = Timer.start(registry);
    try {
      final T result = call.get();
      sample.stop(timer(operation, SUCCESS));
      return result;
    } catch (final RuntimeException e) {
      sample.stop(timer(operation, FAILURE));
      throw e;
    }
  }

  private Timer timer(final String operation, final String outcome) {
    return timers.computeIfAbsent(
        operation + '/' + outcome,
        key ->
            Timer.builder(name)
                .description(description)
                .tag("operation", operation)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry));
  }
}
