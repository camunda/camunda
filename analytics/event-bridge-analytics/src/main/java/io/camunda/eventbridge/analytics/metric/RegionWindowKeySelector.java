/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.analytics.streaming.aggregate.KeySelector;
import io.camunda.analytics.streaming.window.TumblingWindows;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;

/**
 * Selects the {@link RegionWindowKey} a fact aggregates under: the {@code region} variable (or a
 * placeholder when absent), the process coordinates, and the tumbling window its completion time
 * falls into. Deterministic — a given fact always maps to the same key.
 */
public final class RegionWindowKeySelector
    implements KeySelector<ProcessInstanceExecutionTimeFact, RegionWindowKey> {

  /** The variable used as the region grouping dimension. */
  public static final String REGION_VARIABLE = "region";

  /** Placeholder region for instances with no {@code region} variable. */
  public static final String NO_REGION = "<none>";

  private final TumblingWindows windows;

  public RegionWindowKeySelector(final TumblingWindows windows) {
    this.windows = windows;
  }

  @Override
  public RegionWindowKey getKey(final ProcessInstanceExecutionTimeFact fact) {
    final String region = fact.variables().getOrDefault(REGION_VARIABLE, NO_REGION);
    return new RegionWindowKey(
        region,
        fact.processDefinitionKey(),
        fact.version(),
        fact.tenantId(),
        windows.windowStart(fact.endTime()));
  }
}
