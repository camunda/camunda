/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.variable.ProcessVariableWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public class ExternalVariableUpdateDatabaseImportJob extends DatabaseImportJob<ProcessVariableDto> {

  private final ProcessVariableWriter variableWriter;
  private final ConfigurationService configurationService;

  public ExternalVariableUpdateDatabaseImportJob(
      final ProcessVariableWriter variableWriter,
      final ConfigurationService configurationService,
      final Runnable callback,
      final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.variableWriter = variableWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<ProcessVariableDto> variableUpdates) {
    databaseClient.executeImportRequestsAsBulk(
        "External variable updates",
        variableWriter.generateVariableUpdateImports(variableUpdates),
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
