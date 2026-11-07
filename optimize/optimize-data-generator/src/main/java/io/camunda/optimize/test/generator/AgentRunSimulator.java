/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import io.camunda.optimize.test.generator.AgentInstanceEvent.Limits;
import io.camunda.optimize.test.generator.AgentInstanceEvent.Metrics;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Simulates one agent run the way the engine records it, for the agentic dashboard Phase 2 metrics.
 *
 * <ul>
 *   <li>One UPDATED per model call and per tool round, with {@code changedAttributes} = metrics and
 *       status. The run stops being active well before the process ends; a status-only IDLE and the
 *       COMPLETED at process end follow, so run duration differs from process duration.
 *   <li>About 8 % of runs switch from Bedrock to OpenAI mid-run through a config-commit UPDATED.
 *   <li>About 40 % of runs have no limits; about 10 % of all runs are driven to their model call
 *       limit.
 *   <li>Cache: about 30 % report none, 20 % are cache-heavy. Reasoning tokens only for reasoning
 *       models, and always part of output.
 *   <li>Ad-hoc sub-process agents call their tool elements with uneven weights; the per-element
 *       counts are returned so the flow-node walk emits the same number of tool instances.
 *   <li>About 12 % of finished runs get an incident on the agent element, a third of them raised
 *       after the run's last activity.
 * </ul>
 */
class AgentRunSimulator {

  private static final String[][] MODELS = {
    {"gpt-4o", "openai"},
    {"gpt-4o-mini", "openai"},
    {"o3-mini", "openai"},
    {"anthropic.claude-3-5-sonnet", "bedrock"},
    {"gemini-2.0-flash", "google"},
  };
  private static final String[] SWITCH_FROM = {"anthropic.claude-3-5-sonnet", "bedrock"};
  private static final String[] SWITCH_TO = {"gpt-4o", "openai"};
  private static final String REASONING_MODEL = "o3-mini";
  // Uneven tool usage inside the ad-hoc sub-process: the first tool is used most.
  private static final int[] TOOL_WEIGHTS = {5, 3, 1, 1};

  private final Random rng;

  AgentRunSimulator(final Random rng) {
    this.rng = rng;
  }

  AgentRunPlan simulate(
      final InstanceWindow window,
      final long agentInstanceKey,
      final long agentDefinitionKey,
      final long elementInstanceKey,
      final String elementId,
      final List<AgentTool> tools) {
    final Run run =
        new Run(agentInstanceKey, agentDefinitionKey, elementInstanceKey, elementId, tools);
    final long start = window.startMs();
    final long duration = Math.max(0L, window.endMs() - start);

    final boolean switchesModel = rng.nextDouble() < 0.08;
    final String[] llm = switchesModel ? SWITCH_FROM : MODELS[rng.nextInt(MODELS.length)];
    run.model = llm[0];
    run.provider = llm[1];
    run.limits = rng.nextDouble() < 0.4 ? Limits.NONE : randomLimits();
    final boolean drivenToLimit = run.limits != Limits.NONE && rng.nextDouble() < 0.17;
    final double cacheProfile = rng.nextDouble();
    final boolean noCache = cacheProfile < 0.3;
    final boolean cacheHeavy = cacheProfile >= 0.8;

    run.emit(AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, List.of(), start);

    if (window.isActive()) {
      run.modelCall(this, noCache, cacheHeavy, true);
      run.emit(
          AgentInstanceIntent.UPDATED,
          AgentInstanceStatus.THINKING,
          List.of("metrics", "status"),
          start + 1_000L);
      return run.plan(start + 1_000L, null);
    }

    final int modelCalls =
        drivenToLimit
            ? run.limits.maxModelCalls()
            : run.limits == Limits.NONE
                ? 1 + rng.nextInt(6)
                : 1 + rng.nextInt(Math.max(1, run.limits.maxModelCalls() - 1));
    // The run is active for 15-55 % of the process instance, starting shortly after it.
    final long activeStart = start + (long) (duration * 0.02);
    final long activeEnd = start + (long) (duration * (0.15 + rng.nextDouble() * 0.4));
    final long step = Math.max(1L, (activeEnd - activeStart) / (modelCalls * 2L));
    long t = activeStart;
    for (int call = 1; call <= modelCalls; call++) {
      if (switchesModel && call == (modelCalls + 1) / 2 + 1) {
        run.model = SWITCH_TO[0];
        run.provider = SWITCH_TO[1];
        t += step / 2;
        run.emit(
            AgentInstanceIntent.UPDATED,
            AgentInstanceStatus.THINKING,
            List.of("model", "provider"),
            t);
      }
      t += step;
      run.modelCall(this, noCache, cacheHeavy, call == 1);
      run.emit(
          AgentInstanceIntent.UPDATED,
          AgentInstanceStatus.THINKING,
          List.of("metrics", "status"),
          t);
      if (call < modelCalls) {
        final int toolCalls = run.capToolCalls(rng.nextInt(4));
        if (toolCalls > 0) {
          t += step;
          run.toolCalls += toolCalls;
          run.emit(
              AgentInstanceIntent.UPDATED,
              AgentInstanceStatus.TOOL_CALLING,
              List.of("metrics", "status"),
              t);
        }
      }
    }
    final long lastActivity = t;
    run.emit(
        AgentInstanceIntent.UPDATED, AgentInstanceStatus.IDLE, List.of("status"), lastActivity + 1);
    run.emit(
        AgentInstanceIntent.COMPLETED,
        AgentInstanceStatus.COMPLETED,
        List.of("status"),
        window.endMs());

    Long incidentAt = null;
    if (rng.nextDouble() < 0.12) {
      incidentAt =
          rng.nextDouble() < 1.0 / 3
              ? lastActivity + (window.endMs() - lastActivity) / 2
              : activeStart + (lastActivity - activeStart) / 2;
    }
    return run.plan(lastActivity, incidentAt);
  }

  private Limits randomLimits() {
    return new Limits(8_000L + rng.nextInt(16_000), 4 + rng.nextInt(7), 6 + rng.nextInt(15));
  }

  private Map<String, Integer> distributeToolCalls(final List<AgentTool> tools, final int total) {
    final List<AgentTool> bpmnTools =
        tools.stream().filter(tool -> tool.elementId() != null).toList();
    final Map<String, Integer> counts = new LinkedHashMap<>();
    if (bpmnTools.isEmpty() || total == 0) {
      return counts;
    }
    int weightSum = 0;
    for (int i = 0; i < bpmnTools.size(); i++) {
      weightSum += TOOL_WEIGHTS[i % TOOL_WEIGHTS.length];
    }
    for (int call = 0; call < total; call++) {
      int pick = rng.nextInt(weightSum);
      for (int i = 0; i < bpmnTools.size(); i++) {
        pick -= TOOL_WEIGHTS[i % TOOL_WEIGHTS.length];
        if (pick < 0) {
          counts.merge(bpmnTools.get(i).elementId(), 1, Integer::sum);
          break;
        }
      }
    }
    return counts;
  }

  /** Records of one run plus what the rest of the instance needs to stay consistent with it. */
  record AgentRunPlan(
      List<AgentInstanceEvent> events,
      Map<String, Integer> toolCallsByElement,
      long lastActivityMs,
      Long incidentAtMs,
      long elementInstanceKey,
      String elementId) {}

  /** Mutable state of one run while it is simulated. */
  private final class Run {

    private final long agentInstanceKey;
    private final long agentDefinitionKey;
    private final long elementInstanceKey;
    private final String elementId;
    private final List<AgentTool> tools;
    private final List<AgentInstanceEvent> events = new ArrayList<>();
    private String model;
    private String provider;
    private Limits limits;
    private long input;
    private long output;
    private long reasoning = -1L;
    private long cacheCreation = -1L;
    private long cacheRead = -1L;
    private int modelCalls;
    private int toolCalls;

    private Run(
        final long agentInstanceKey,
        final long agentDefinitionKey,
        final long elementInstanceKey,
        final String elementId,
        final List<AgentTool> tools) {
      this.agentInstanceKey = agentInstanceKey;
      this.agentDefinitionKey = agentDefinitionKey;
      this.elementInstanceKey = elementInstanceKey;
      this.elementId = elementId;
      this.tools = tools;
    }

    private void modelCall(
        final AgentRunSimulator sim,
        final boolean noCache,
        final boolean cacheHeavy,
        final boolean firstCall) {
      modelCalls++;
      input += 300 + sim.rng.nextInt(900);
      final long callOutput = 80 + sim.rng.nextInt(350);
      if (REASONING_MODEL.equals(model)) {
        final long callReasoning = 100 + sim.rng.nextInt(500);
        reasoning = Math.max(0L, reasoning) + callReasoning;
        output += callOutput + callReasoning;
      } else {
        output += callOutput;
      }
      if (!noCache) {
        cacheCreation =
            Math.max(0L, cacheCreation) + (firstCall ? 1_000 + sim.rng.nextInt(2_000) : 0);
        cacheRead =
            Math.max(0L, cacheRead)
                + (cacheHeavy ? 1_500 + sim.rng.nextInt(2_500) : 50 + sim.rng.nextInt(350));
      }
    }

    private int capToolCalls(final int requested) {
      if (limits.maxToolCalls() < 0) {
        return requested;
      }
      return Math.max(0, Math.min(requested, limits.maxToolCalls() - toolCalls));
    }

    private void emit(
        final AgentInstanceIntent intent,
        final AgentInstanceStatus status,
        final List<String> changedAttributes,
        final long timestamp) {
      events.add(
          new AgentInstanceEvent(
              agentInstanceKey,
              agentDefinitionKey,
              elementInstanceKey,
              elementId,
              intent,
              status,
              model,
              provider,
              limits,
              new Metrics(
                  input, output, reasoning, cacheCreation, cacheRead, modelCalls, toolCalls),
              tools,
              changedAttributes,
              timestamp));
    }

    private AgentRunPlan plan(final long lastActivityMs, final Long incidentAtMs) {
      return new AgentRunPlan(
          List.copyOf(events),
          distributeToolCalls(tools, toolCalls),
          lastActivityMs,
          incidentAtMs,
          elementInstanceKey,
          elementId);
    }
  }
}
