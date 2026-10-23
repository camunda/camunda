/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.AgentDefinitionWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;

public class AgentDefinitionDatabaseImportJob extends DatabaseImportJob<AgentDefinitionDto> {

  private final AgentDefinitionWriter agentDefinitionWriter;
  private final ConfigurationService configurationService;

  public AgentDefinitionDatabaseImportJob(
      final AgentDefinitionWriter agentDefinitionWriter,
      final ConfigurationService configurationService,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient,
      final int partitionId) {
    super(importCompleteCallback, databaseClient, ValueType.AGENT_DEFINITION.name(), partitionId);
    this.agentDefinitionWriter = agentDefinitionWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<AgentDefinitionDto> agentDefinitions) {
    databaseClient.executeImportRequestsAsBulk(
        "Zeebe agent definitions",
        agentDefinitionWriter.generateAgentDefinitionImports(agentDefinitions),
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
