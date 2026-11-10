/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c7bench.engine;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.springframework.stereotype.Component;

/**
 * Registers the benchmark instrumentation with the engine.
 *
 * <ul>
 *   <li>{@link CommandMetricsInterceptor}: duration and outcome of every engine command (one
 *       command = one DB transaction).
 *   <li>{@link MetricsHistoryEventHandler}: process instance and external task lifecycle counters
 *       plus the process instance duration histogram, recorded on transaction commit.
 * </ul>
 *
 * <p>The default DB history handler stays enabled, so the history tables grow as in production.
 */
@Component
public class BenchmarkMetricsPlugin extends AbstractProcessEnginePlugin {

  private final MeterRegistry registry;

  public BenchmarkMetricsPlugin(final MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void preInit(final ProcessEngineConfigurationImpl config) {
    if (config.getCustomPreCommandInterceptorsTxRequired() == null) {
      config.setCustomPreCommandInterceptorsTxRequired(new ArrayList<>());
    }
    config.getCustomPreCommandInterceptorsTxRequired().add(new CommandMetricsInterceptor(registry));

    if (config.getCustomHistoryEventHandlers() == null) {
      config.setCustomHistoryEventHandlers(new ArrayList<>());
    }
    config.getCustomHistoryEventHandlers().add(new MetricsHistoryEventHandler(registry));
  }
}
