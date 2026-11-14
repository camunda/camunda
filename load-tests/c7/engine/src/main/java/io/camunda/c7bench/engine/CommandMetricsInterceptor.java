/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c7bench.engine;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.camunda.bpm.engine.OptimisticLockingException;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;

/**
 * Times every top-level engine command, including the commit of its transaction.
 *
 * <p>Metric: {@code c7_command_duration_seconds{command, outcome}}. The count of this timer is the
 * number of engine transactions, which together with the PostgreSQL WAL metrics gives WAL bytes
 * per command. Nested commands (a command calling the engine API again) are not recorded
 * separately.
 */
public class CommandMetricsInterceptor extends CommandInterceptor {

  private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

  private final MeterRegistry registry;

  public CommandMetricsInterceptor(final MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public <T> T execute(final Command<T> command) {
    final int[] depth = DEPTH.get();
    if (depth[0] > 0) {
      return next.execute(command);
    }

    depth[0]++;
    final long start = System.nanoTime();
    String outcome = "success";
    try {
      return next.execute(command);
    } catch (final OptimisticLockingException e) {
      outcome = "optimistic_locking";
      throw e;
    } catch (final RuntimeException e) {
      outcome = "error";
      throw e;
    } finally {
      depth[0]--;
      timer(commandName(command), outcome).record(Duration.ofNanos(System.nanoTime() - start));
    }
  }

  private Timer timer(final String command, final String outcome) {
    return Timer.builder("c7.command.duration")
        .description("Duration of top-level engine commands including transaction commit")
        .tag("command", command)
        .tag("outcome", outcome)
        .publishPercentileHistogram()
        .minimumExpectedValue(Duration.ofMillis(1))
        .maximumExpectedValue(Duration.ofSeconds(60))
        .register(registry);
  }

  /** Keeps label cardinality bounded: lambdas and anonymous classes map to their outer class. */
  static String commandName(final Command<?> command) {
    final String name = command.getClass().getName();
    final String simple = name.substring(name.lastIndexOf('.') + 1);
    final int dollar = simple.indexOf('$');
    return dollar > 0 ? simple.substring(0, dollar) + "$anon" : simple;
  }
}
