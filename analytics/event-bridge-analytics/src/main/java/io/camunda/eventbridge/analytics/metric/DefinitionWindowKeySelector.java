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
 * Selects the {@link DefinitionWindowKey} for a fact: the process coordinates and the tumbling
 * window of its completion time, with no region — so facts from all regions roll up together.
 */
public final class DefinitionWindowKeySelector
    implements KeySelector<ProcessInstanceExecutionTimeFact, DefinitionWindowKey> {

  private final TumblingWindows windows;

  public DefinitionWindowKeySelector(final TumblingWindows windows) {
    this.windows = windows;
  }

  @Override
  public DefinitionWindowKey getKey(final ProcessInstanceExecutionTimeFact fact) {
    return new DefinitionWindowKey(
        fact.processDefinitionKey(),
        fact.version(),
        fact.tenantId(),
        windows.windowStart(fact.endTime()));
  }
}
