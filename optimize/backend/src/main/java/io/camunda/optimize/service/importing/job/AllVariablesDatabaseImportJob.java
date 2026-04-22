/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.importing.AllVariablesDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.AllVariablesWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public class AllVariablesDatabaseImportJob extends DatabaseImportJob<AllVariablesDto> {

  private final AllVariablesWriter allVariablesWriter;
  private final ConfigurationService configurationService;

  public AllVariablesDatabaseImportJob(
      final AllVariablesWriter allVariablesWriter,
      final ConfigurationService configurationService,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.allVariablesWriter = allVariablesWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<AllVariablesDto> documents) {
    final List<ImportRequestDto> importRequests = allVariablesWriter.generateImports(documents);
    databaseClient.executeImportRequestsAsBulk(
        "Zeebe all variables",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
