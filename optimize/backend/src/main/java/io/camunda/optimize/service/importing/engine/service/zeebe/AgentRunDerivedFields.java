/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Fields computed from other fields of an agent run. The writer's merge script mirrors these rules
 * for runs that already exist, so both must change together.
 */
public final class AgentRunDerivedFields {

  public static final String LIMIT_MODEL_CALLS = "MODEL_CALLS";
  public static final String LIMIT_TOOL_CALLS = "TOOL_CALLS";
  public static final String LIMIT_TOKENS = "TOKENS";

  /** In the order the writer's merge script checks them, which passes them in as a parameter. */
  public static final List<String> LIMITS =
      List.of(LIMIT_MODEL_CALLS, LIMIT_TOOL_CALLS, LIMIT_TOKENS);

  private AgentRunDerivedFields() {}

  public static void apply(final AgentRunDto run) {
    run.setTotalTokens(totalTokens(run));
    final List<String> limitsHit = limitsHit(run);
    run.setLimitHit(limitsHit);
    run.setHitLimit(!limitsHit.isEmpty());
  }

  /**
   * Input, output and both cache buckets. The connector reports cache tokens separately from input
   * and reasoning tokens as part of output, so reasoning is not added again. Null when no component
   * was reported.
   */
  static Long totalTokens(final AgentRunDto run) {
    final List<Long> components =
        Stream.of(
                run.getInputTokens(),
                run.getOutputTokens(),
                run.getCacheCreationTokens(),
                run.getCacheReadTokens())
            .toList();
    if (components.stream().allMatch(c -> c == null)) {
      return null;
    }
    return components.stream().mapToLong(c -> c == null ? 0L : c).sum();
  }

  /** A limit counts as reached when it is set ({@code > 0}) and the run got to it. */
  static List<String> limitsHit(final AgentRunDto run) {
    final List<String> hit = new ArrayList<>();
    if (reached(run.getModelCalls(), run.getMaxModelCalls())) {
      hit.add(LIMIT_MODEL_CALLS);
    }
    if (reached(run.getToolCalls(), run.getMaxToolCalls())) {
      hit.add(LIMIT_TOOL_CALLS);
    }
    if (reached(run.getTotalTokens(), run.getMaxTokens())) {
      hit.add(LIMIT_TOKENS);
    }
    return hit;
  }

  private static boolean reached(final Long value, final Long limit) {
    return limit != null && limit > 0 && value != null && value >= limit;
  }
}
