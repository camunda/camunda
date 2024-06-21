/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import java.util.List;

public class ProcessDefinitionDatabaseImportJob
    extends DatabaseImportJob<ProcessDefinitionOptimizeDto> {

  private final ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionDatabaseImportJob(
      final ProcessDefinitionWriter processDefinitionWriter,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  protected void persistEntities(final List<ProcessDefinitionOptimizeDto> newOptimizeEntities) {
    processDefinitionWriter.importProcessDefinitions(newOptimizeEntities);
  }
}
