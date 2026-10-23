/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.AgentDefinitionWriter;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.engine.service.zeebe.AgentRunRecordFolder.FoldResult;
import io.camunda.optimize.service.importing.job.AgentRunDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Imports agent instance records into the flat agent-instance index, one document per run. Runs
 * next to the nested import ({@link ZeebeAgentInstanceImportService}) with its own import position,
 * so both read the same records independently.
 */
public class ZeebeAgentRunImportService implements ImportService<ZeebeAgentInstanceRecordDto> {

  private static final Set<AgentInstanceIntent> INTENTS_TO_IMPORT =
      Set.of(
          AgentInstanceIntent.CREATED, AgentInstanceIntent.UPDATED, AgentInstanceIntent.COMPLETED);
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeAgentRunImportService.class);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final AgentRunWriter agentRunWriter;
  private final AgentDefinitionWriter agentDefinitionWriter;
  private final ConfigurationService configurationService;
  private final int partitionId;
  private final DatabaseClient databaseClient;

  public ZeebeAgentRunImportService(
      final ConfigurationService configurationService,
      final AgentRunWriter agentRunWriter,
      final AgentDefinitionWriter agentDefinitionWriter,
      final int partitionId,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.agentRunWriter = agentRunWriter;
    this.agentDefinitionWriter = agentDefinitionWriter;
    this.configurationService = configurationService;
    this.partitionId = partitionId;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<ZeebeAgentInstanceRecordDto> pageOfRecords,
      final Runnable importCompleteCallback) {
    LOG.trace("Importing agent runs from zeebe records...");
    if (!pageOfRecords.isEmpty()) {
      final AgentRunDatabaseImportJob importJob =
          new AgentRunDatabaseImportJob(
              agentRunWriter,
              agentDefinitionWriter,
              configurationService,
              importCompleteCallback,
              databaseClient,
              partitionId);
      importJob.setEntitiesToImport(foldRuns(pageOfRecords));
      databaseImportJobExecutor.executeImportJob(importJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  static List<FoldResult> foldRuns(final List<ZeebeAgentInstanceRecordDto> records) {
    final List<FoldResult> runs =
        records.stream()
            .filter(record -> INTENTS_TO_IMPORT.contains(record.getIntent()))
            .collect(
                Collectors.groupingBy(
                    ZeebeAgentInstanceRecordDto::getKey, LinkedHashMap::new, Collectors.toList()))
            .values()
            .stream()
            .map(AgentRunRecordFolder::fold)
            .toList();
    LOG.debug(
        "Processing {} fetched zeebe agent instance records into {} agent runs.",
        records.size(),
        runs.size());
    return runs;
  }
}
