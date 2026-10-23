/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.AgentDefinitionWriter;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.engine.service.zeebe.AgentRunRecordFolder.FoldResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;

public class AgentRunDatabaseImportJob extends DatabaseImportJob<FoldResult> {

  private final AgentRunWriter agentRunWriter;
  private final AgentDefinitionWriter agentDefinitionWriter;
  private final ConfigurationService configurationService;

  public AgentRunDatabaseImportJob(
      final AgentRunWriter agentRunWriter,
      final AgentDefinitionWriter agentDefinitionWriter,
      final ConfigurationService configurationService,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient,
      final int partitionId) {
    super(importCompleteCallback, databaseClient, ValueType.AGENT_INSTANCE.name(), partitionId);
    this.agentRunWriter = agentRunWriter;
    this.agentDefinitionWriter = agentDefinitionWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<FoldResult> foldedRuns) {
    databaseClient.executeImportRequestsAsBulk(
        "Zeebe agent runs",
        agentRunWriter.generateAgentRunImports(foldedRuns),
        configurationService.getSkipDataAfterNestedDocLimitReached());
    databaseClient.executeImportRequestsAsBulk(
        "Zeebe agent definition rollups",
        agentDefinitionWriter.generateAgentRunRollupImports(
            foldedRuns.stream().map(FoldResult::run).toList()),
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
