/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentIds;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto.MetricTotalsDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto.ModelTokensDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto.ToolCallsDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceRecordDto;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Folds the agent instance records of one run (one import page) into an agent run document.
 *
 * <p>The result is the complete document for a first insert. When the run already exists, the
 * writer's merge script applies the same rules on top of the stored document, using {@link
 * FoldResult#metricObservations()} to split tokens by model relative to what is already stored.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>Engine events carry running totals; {@code -1} means "never reported" and is stored as
 *       null.
 *   <li>Config, metrics and status come from the record with the highest position.
 *   <li>{@code lastActivityDate} is the latest timestamp of {@code CREATED} and of every {@code
 *       UPDATED} whose {@code changedAttributes} contains {@code metrics}. {@code COMPLETED} only
 *       changes the status, and a configuration commit only changes the config, so neither moves
 *       it.
 *   <li>Tokens are split by the model in effect on the record that reported them, as the difference
 *       between consecutive metric totals.
 * </ul>
 */
public final class AgentRunRecordFolder {

  private AgentRunRecordFolder() {}

  public static FoldResult fold(final List<ZeebeAgentInstanceRecordDto> recordsOfOneRun) {
    final List<ZeebeAgentInstanceRecordDto> ordered =
        recordsOfOneRun.stream()
            .sorted(Comparator.comparingLong(ZeebeAgentInstanceRecordDto::getPosition))
            .toList();
    final AgentRunDto run = new AgentRunDto();
    final List<MetricObservation> observations = new ArrayList<>();

    for (final ZeebeAgentInstanceRecordDto record : ordered) {
      final ZeebeAgentInstanceDataDto value = record.getValue();
      final OffsetDateTime recordDate = toDate(record.getTimestamp());

      applyIdentity(run, record);
      applyLatestState(run, value);
      run.setLastRecordPosition(record.getPosition());
      addTools(run, value);

      if (record.getIntent() == AgentInstanceIntent.CREATED) {
        run.setStartDate(recordDate);
      }
      if (record.getIntent() == AgentInstanceIntent.COMPLETED) {
        run.setEndDate(recordDate);
      }
      if (changesActivity(record)) {
        run.setLastActivityDate(recordDate);
        observations.add(MetricObservation.of(record));
      }
    }

    applyTokensByModel(run, observations);
    AgentRunDerivedFields.apply(run);
    return new FoldResult(run, observations);
  }

  static boolean changesActivity(final ZeebeAgentInstanceRecordDto record) {
    return record.getIntent() == AgentInstanceIntent.CREATED
        || (record.getIntent() == AgentInstanceIntent.UPDATED
            && record
                .getValue()
                .getChangedAttributes()
                .contains(AgentInstanceRecordValue.ATTR_METRICS));
  }

  /**
   * Splits tokens by model, starting from zero. Shares its rules with the writer's merge script,
   * which continues from the totals already stored on the document.
   */
  private static void applyTokensByModel(
      final AgentRunDto run, final List<MetricObservation> observations) {
    MetricTotalsDto previous = zeroTotals();
    for (final MetricObservation observation : observations) {
      final ModelTokensDto entry = findOrAddModelEntry(run, observation);
      final MetricTotalsDto current = observation.totals(previous);
      add(entry, previous, current);
      previous = current;
    }
    run.setLastMetricTotals(observations.isEmpty() ? null : previous);
  }

  private static ModelTokensDto findOrAddModelEntry(
      final AgentRunDto run, final MetricObservation observation) {
    return run.getTokensByModel().stream()
        .filter(
            e ->
                Objects.equals(e.getProvider(), observation.provider())
                    && Objects.equals(e.getModel(), observation.model()))
        .findFirst()
        .orElseGet(
            () -> {
              final ModelTokensDto entry = new ModelTokensDto();
              entry.setProvider(observation.provider());
              entry.setModel(observation.model());
              entry.setInputTokens(0L);
              entry.setOutputTokens(0L);
              entry.setReasoningTokens(0L);
              entry.setCacheCreationTokens(0L);
              entry.setCacheReadTokens(0L);
              entry.setTotalTokens(0L);
              run.getTokensByModel().add(entry);
              return entry;
            });
  }

  private static void add(
      final ModelTokensDto entry, final MetricTotalsDto previous, final MetricTotalsDto current) {
    final long input = delta(previous.getInputTokens(), current.getInputTokens());
    final long output = delta(previous.getOutputTokens(), current.getOutputTokens());
    final long reasoning = delta(previous.getReasoningTokens(), current.getReasoningTokens());
    final long cacheCreation =
        delta(previous.getCacheCreationTokens(), current.getCacheCreationTokens());
    final long cacheRead = delta(previous.getCacheReadTokens(), current.getCacheReadTokens());
    entry.setInputTokens(entry.getInputTokens() + input);
    entry.setOutputTokens(entry.getOutputTokens() + output);
    entry.setReasoningTokens(entry.getReasoningTokens() + reasoning);
    entry.setCacheCreationTokens(entry.getCacheCreationTokens() + cacheCreation);
    entry.setCacheReadTokens(entry.getCacheReadTokens() + cacheRead);
    // Reasoning is already part of output, so it is not added again.
    entry.setTotalTokens(entry.getTotalTokens() + input + output + cacheCreation + cacheRead);
  }

  private static long delta(final Long previous, final Long current) {
    return Math.max(0L, current - previous);
  }

  private static void applyIdentity(
      final AgentRunDto run, final ZeebeAgentInstanceRecordDto record) {
    final ZeebeAgentInstanceDataDto value = record.getValue();
    run.setAgentInstanceId(String.valueOf(record.getKey()));
    if (value.getAgentDefinitionKey() > 0) {
      run.setAgentDefinitionId(String.valueOf(value.getAgentDefinitionKey()));
    }
    run.setAgentId(AgentIds.agentId(value.getBpmnProcessId(), value.getElementId()));
    run.setFlowNodeId(value.getElementId());
    run.setProcessDefinitionKey(value.getBpmnProcessId());
    run.setProcessDefinitionId(String.valueOf(value.getProcessDefinitionKey()));
    run.setProcessDefinitionVersion(String.valueOf(value.getProcessDefinitionVersion()));
    run.setProcessInstanceId(String.valueOf(value.getProcessInstanceKey()));
    run.setRootProcessInstanceId(
        value.getRootProcessInstanceKey() > 0
            ? String.valueOf(value.getRootProcessInstanceKey())
            : String.valueOf(value.getProcessInstanceKey()));
    run.setTenantId(value.getTenantId());
  }

  private static void applyLatestState(
      final AgentRunDto run, final ZeebeAgentInstanceDataDto value) {
    if (value.getStatus() != null) {
      run.setStatus(value.getStatus().name());
    }
    if (!value.getElementInstanceKeys().isEmpty()) {
      run.setFlowNodeInstanceIds(
          value.getElementInstanceKeys().stream().map(String::valueOf).toList());
    }
    if (value.getDefinition() != null) {
      run.setModel(emptyToNull(value.getDefinition().getModel()));
      run.setProvider(emptyToNull(value.getDefinition().getProvider()));
    }
    if (value.getLimits() != null) {
      run.setMaxModelCalls((long) value.getLimits().getMaxModelCalls());
      run.setMaxToolCalls((long) value.getLimits().getMaxToolCalls());
      run.setMaxTokens(value.getLimits().getMaxTokens());
    }
    final ZeebeAgentInstanceDataDto.AgentMetricsValueDto metrics = value.getMetrics();
    if (metrics != null) {
      run.setInputTokens(reported(metrics.getInputTokens()));
      run.setOutputTokens(reported(metrics.getOutputTokens()));
      run.setReasoningTokens(reported(metrics.getReasoningTokenCount()));
      run.setCacheCreationTokens(reported(metrics.getCacheCreationTokenCount()));
      run.setCacheReadTokens(reported(metrics.getCacheReadTokenCount()));
      run.setModelCalls(reported(metrics.getModelCalls()));
      run.setToolCalls(reported(metrics.getToolCalls()));
    }
  }

  private static void addTools(final AgentRunDto run, final ZeebeAgentInstanceDataDto value) {
    value.getTools().stream()
        .filter(tool -> tool.getElementId() != null && !tool.getElementId().isEmpty())
        .forEach(
            tool -> {
              if (!run.getToolFlowNodeIds().contains(tool.getElementId())) {
                run.getToolFlowNodeIds().add(tool.getElementId());
                final ToolCallsDto toolCalls = new ToolCallsDto();
                toolCalls.setToolFlowNodeId(tool.getElementId());
                toolCalls.setToolName(tool.getName());
                run.getToolCallsByTool().add(toolCalls);
              }
            });
  }

  static Long reported(final long value) {
    return value < 0 ? null : value;
  }

  private static String emptyToNull(final String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private static OffsetDateTime toDate(final long timestamp) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
  }

  private static MetricTotalsDto zeroTotals() {
    final MetricTotalsDto totals = new MetricTotalsDto();
    totals.setInputTokens(0L);
    totals.setOutputTokens(0L);
    totals.setReasoningTokens(0L);
    totals.setCacheCreationTokens(0L);
    totals.setCacheReadTokens(0L);
    return totals;
  }

  public record FoldResult(AgentRunDto run, List<MetricObservation> metricObservations)
      implements OptimizeDto {}

  /**
   * Running token totals and the model in effect on one activity record. A component the record did
   * not report is null and keeps the previous total.
   */
  public record MetricObservation(
      long position,
      String provider,
      String model,
      Long inputTokens,
      Long outputTokens,
      Long reasoningTokens,
      Long cacheCreationTokens,
      Long cacheReadTokens) {

    static MetricObservation of(final ZeebeAgentInstanceRecordDto record) {
      final ZeebeAgentInstanceDataDto value = record.getValue();
      final ZeebeAgentInstanceDataDto.AgentMetricsValueDto metrics = value.getMetrics();
      return new MetricObservation(
          record.getPosition(),
          value.getDefinition() == null ? null : emptyToNull(value.getDefinition().getProvider()),
          value.getDefinition() == null ? null : emptyToNull(value.getDefinition().getModel()),
          metrics == null ? null : reported(metrics.getInputTokens()),
          metrics == null ? null : reported(metrics.getOutputTokens()),
          metrics == null ? null : reported(metrics.getReasoningTokenCount()),
          metrics == null ? null : reported(metrics.getCacheCreationTokenCount()),
          metrics == null ? null : reported(metrics.getCacheReadTokenCount()));
    }

    MetricTotalsDto totals(final MetricTotalsDto previous) {
      final MetricTotalsDto totals = new MetricTotalsDto();
      totals.setInputTokens(Objects.requireNonNullElse(inputTokens, previous.getInputTokens()));
      totals.setOutputTokens(Objects.requireNonNullElse(outputTokens, previous.getOutputTokens()));
      totals.setReasoningTokens(
          Objects.requireNonNullElse(reasoningTokens, previous.getReasoningTokens()));
      totals.setCacheCreationTokens(
          Objects.requireNonNullElse(cacheCreationTokens, previous.getCacheCreationTokens()));
      totals.setCacheReadTokens(
          Objects.requireNonNullElse(cacheReadTokens, previous.getCacheReadTokens()));
      return totals;
    }
  }
}
