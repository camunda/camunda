/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.util.List;

/**
 * One AGENT_INSTANCE event as the engine would export it: metrics are running totals, {@code -1}
 * means "never reported", and {@code changedAttributes} names what this event changed.
 */
record AgentInstanceEvent(
    long agentInstanceKey,
    long agentDefinitionKey,
    long elementInstanceKey,
    String elementId,
    AgentInstanceIntent intent,
    AgentInstanceStatus status,
    String model,
    String provider,
    Limits limits,
    Metrics metrics,
    List<AgentTool> tools,
    List<String> changedAttributes,
    long timestamp) {

  record Limits(long maxTokens, int maxModelCalls, int maxToolCalls) {

    static final Limits NONE = new Limits(-1L, -1, -1);
  }

  record Metrics(
      long inputTokens,
      long outputTokens,
      long reasoningTokens,
      long cacheCreationTokens,
      long cacheReadTokens,
      int modelCalls,
      int toolCalls) {}
}
