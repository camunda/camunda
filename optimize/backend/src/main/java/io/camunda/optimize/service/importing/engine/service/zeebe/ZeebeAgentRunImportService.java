/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.query.agent.AgentIds;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentLimitsValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentMetricsValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.AgentRunDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * Imports agent instance records into the flat agent run index, one document per run. It reads the
 * same records as {@link ZeebeAgentInstanceImportService}, which keeps writing the nested Phase 1
 * list on the process instance, but stores its own import position.
 */
public class ZeebeAgentRunImportService implements ImportService<ZeebeAgentInstanceRecordDto> {

  static final String LIMIT_MODEL_CALLS = "MODEL_CALLS";
  static final String LIMIT_TOOL_CALLS = "TOOL_CALLS";
  static final String LIMIT_TOKENS = "TOKENS";

  private static final Set<AgentInstanceIntent> INTENTS_TO_IMPORT =
      Set.of(
          AgentInstanceIntent.CREATED, AgentInstanceIntent.UPDATED, AgentInstanceIntent.COMPLETED);
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeAgentRunImportService.class);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final AgentRunWriter agentRunWriter;
  private final ConfigurationService configurationService;
  private final int partitionId;
  private final DatabaseClient databaseClient;

  public ZeebeAgentRunImportService(
      final ConfigurationService configurationService,
      final AgentRunWriter agentRunWriter,
      final int partitionId,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.agentRunWriter = agentRunWriter;
    this.configurationService = configurationService;
    this.partitionId = partitionId;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<ZeebeAgentInstanceRecordDto> pageOfAgentInstances,
      final Runnable importCompleteCallback) {
    LOG.trace("Importing agent runs from zeebe records...");

    final boolean newDataIsAvailable = !pageOfAgentInstances.isEmpty();
    if (newDataIsAvailable) {
      final List<AgentRunDto> agentRuns =
          filterAndMapZeebeRecordsToOptimizeEntities(pageOfAgentInstances);
      final AgentRunDatabaseImportJob databaseImportJob =
          new AgentRunDatabaseImportJob(
              agentRunWriter,
              configurationService,
              importCompleteCallback,
              databaseClient,
              partitionId);
      databaseImportJob.setEntitiesToImport(agentRuns);
      addDatabaseImportJobToQueue(databaseImportJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private void addDatabaseImportJobToQueue(final DatabaseImportJob<AgentRunDto> databaseImportJob) {
    databaseImportJobExecutor.executeImportJob(databaseImportJob);
  }

  /**
   * Folds the page into one entity per run, so the writer sends one upsert per run document rather
   * than one per record of the same run.
   */
  static List<AgentRunDto> filterAndMapZeebeRecordsToOptimizeEntities(
      final List<ZeebeAgentInstanceRecordDto> zeebeRecords) {
    final Map<Long, List<ZeebeAgentInstanceRecordDto>> recordsByRun = new LinkedHashMap<>();
    zeebeRecords.stream()
        .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
        .forEach(
            zeebeRecord ->
                recordsByRun
                    .computeIfAbsent(zeebeRecord.getKey(), key -> new ArrayList<>())
                    .add(zeebeRecord));
    final List<AgentRunDto> agentRuns =
        recordsByRun.values().stream().map(ZeebeAgentRunImportService::foldRun).toList();
    LOG.debug(
        "Processing {} fetched zeebe agent instance records into {} agent runs.",
        zeebeRecords.size(),
        agentRuns.size());
    return agentRuns;
  }

  /**
   * Folds the records of one run. The engine sends running totals, not increments, so the newest
   * record holds the current state of the run. Records are ordered by position: all records of one
   * run are on one partition, where positions only go up, while several records can share a
   * timestamp.
   *
   * <p><b>Terminology note:</b> Optimize uses C7 conventions, so the BPMN process ID lands in
   * {@code processDefinitionKey} and the engine's process definition key in {@code
   * processDefinitionId}. See {@code optimize/docs/adr/001-c7-naming-conventions.md}.
   */
  private static AgentRunDto foldRun(final List<ZeebeAgentInstanceRecordDto> recordsOfRun) {
    final List<ZeebeAgentInstanceRecordDto> records =
        recordsOfRun.stream()
            .sorted(Comparator.comparingLong(ZeebeAgentInstanceRecordDto::getPosition))
            .toList();
    final ZeebeAgentInstanceRecordDto first = records.getFirst();
    final ZeebeAgentInstanceRecordDto newest = records.getLast();
    final ZeebeAgentInstanceDataDto value = newest.getValue();

    final AgentRunDto run = new AgentRunDto();
    run.setAgentInstanceId(String.valueOf(newest.getKey()));
    run.setAgentId(AgentIds.agentId(value.getBpmnProcessId(), value.getElementId()));
    run.setFlowNodeId(value.getElementId());
    run.setFlowNodeInstanceIds(
        value.getElementInstanceKeys().stream().map(String::valueOf).toList());
    run.setProcessDefinitionKey(value.getBpmnProcessId());
    run.setProcessDefinitionId(String.valueOf(value.getProcessDefinitionKey()));
    run.setProcessDefinitionVersion(String.valueOf(value.getProcessDefinitionVersion()));
    run.setProcessInstanceId(String.valueOf(value.getProcessInstanceKey()));
    run.setRootProcessInstanceId(String.valueOf(value.getRootProcessInstanceKey()));
    run.setTenantId(value.getTenantId());

    if (value.getDefinition() != null) {
      run.setModel(StringUtils.defaultIfBlank(value.getDefinition().getModel(), null));
      run.setProvider(StringUtils.defaultIfBlank(value.getDefinition().getProvider(), null));
    }
    mapLimits(run, value.getLimits());
    mapUsage(run, value.getMetrics());
    run.setLimitHit(limitsHit(run));

    if (value.getStatus() != null) {
      run.setStatus(value.getStatus().name());
    }
    // Without its CREATED record (removed by Zeebe before the upgrade), the run starts at the
    // oldest record Optimize still reads.
    run.setStartDate(dateForTimestamp(first.getTimestamp()));
    run.setLastActivityDate(dateForTimestamp(lastUsageChange(records).getTimestamp()));
    records.stream()
        .filter(zeebeRecord -> zeebeRecord.getIntent() == AgentInstanceIntent.COMPLETED)
        .findFirst()
        .ifPresent(completed -> run.setEndDate(dateForTimestamp(completed.getTimestamp())));
    run.setLastRecordPosition(newest.getPosition());
    return run;
  }

  private static void mapLimits(final AgentRunDto run, final AgentLimitsValueDto limits) {
    if (limits == null) {
      return;
    }
    run.setMaxTokens(limits.getMaxTokens());
    run.setMaxModelCalls((long) limits.getMaxModelCalls());
    run.setMaxToolCalls((long) limits.getMaxToolCalls());
  }

  private static void mapUsage(final AgentRunDto run, final AgentMetricsValueDto metrics) {
    if (metrics == null) {
      return;
    }
    run.setInputTokens(metrics.getInputTokens());
    run.setOutputTokens(metrics.getOutputTokens());
    run.setReasoningTokens(metrics.getReasoningTokenCount());
    run.setCacheWriteTokens(metrics.getCacheCreationTokenCount());
    run.setCacheReadTokens(metrics.getCacheReadTokenCount());
    run.setTotalTokens(
        metrics.getInputTokens()
            + metrics.getOutputTokens()
            + metrics.getCacheCreationTokenCount()
            + metrics.getCacheReadTokenCount());
    run.setModelCalls((long) metrics.getModelCalls());
    run.setToolCalls((long) metrics.getToolCalls());
  }

  private static List<String> limitsHit(final AgentRunDto run) {
    final List<String> limitsHit = new ArrayList<>();
    if (reached(run.getModelCalls(), run.getMaxModelCalls())) {
      limitsHit.add(LIMIT_MODEL_CALLS);
    }
    if (reached(run.getToolCalls(), run.getMaxToolCalls())) {
      limitsHit.add(LIMIT_TOOL_CALLS);
    }
    if (reached(run.getTotalTokens(), run.getMaxTokens())) {
      limitsHit.add(LIMIT_TOKENS);
    }
    return limitsHit;
  }

  // A limit of -1 means no limit.
  private static boolean reached(final Long usage, final Long limit) {
    return usage != null && limit != null && limit > 0 && usage >= limit;
  }

  /**
   * The newest record that changed the usage of the run. The first record of the page counts as a
   * change: the writer keeps the stored date when the stored usage is the same as the page's.
   */
  private static ZeebeAgentInstanceRecordDto lastUsageChange(
      final List<ZeebeAgentInstanceRecordDto> records) {
    ZeebeAgentInstanceRecordDto lastChange = records.getFirst();
    for (int i = 1; i < records.size(); i++) {
      if (!Objects.equals(
          records.get(i).getValue().getMetrics(), records.get(i - 1).getValue().getMetrics())) {
        lastChange = records.get(i);
      }
    }
    return lastChange;
  }

  private static OffsetDateTime dateForTimestamp(final long timestamp) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
  }
}
