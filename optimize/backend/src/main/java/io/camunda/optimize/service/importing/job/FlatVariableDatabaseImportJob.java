/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.FlatVariableDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.VariableWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public class FlatVariableDatabaseImportJob extends DatabaseImportJob<FlatVariableDto> {

  private final VariableWriter variableWriter;
  private final ConfigurationService configurationService;

  public FlatVariableDatabaseImportJob(
      final VariableWriter variableWriter,
      final ConfigurationService configurationService,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.variableWriter = variableWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<FlatVariableDto> variables) {
    final List<ImportRequestDto> importRequests =
        variableWriter.generateFlatVariableImports(variables);
    databaseClient.executeImportRequestsAsBulk(
        "flat variables",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
