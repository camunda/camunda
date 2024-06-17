/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import io.camunda.optimize.service.CamundaEventImportService;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.variable.ProcessVariableUpdateWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.List;

public class VariableUpdateDatabaseImportJob extends DatabaseImportJob<ProcessVariableDto> {

  private final ProcessVariableUpdateWriter processVariableUpdateWriter;
  private final CamundaEventImportService camundaEventImportService;
  private final ConfigurationService configurationService;

  public VariableUpdateDatabaseImportJob(
      final ProcessVariableUpdateWriter variableWriter,
      final CamundaEventImportService camundaEventImportService,
      final ConfigurationService configurationService,
      final Runnable callback,
      final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.processVariableUpdateWriter = variableWriter;
    this.camundaEventImportService = camundaEventImportService;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<ProcessVariableDto> variableUpdates) {
    List<ImportRequestDto> importBulks = new ArrayList<>();
    importBulks.addAll(processVariableUpdateWriter.generateVariableUpdateImports(variableUpdates));
    importBulks.addAll(camundaEventImportService.generateVariableUpdateImports(variableUpdates));
    databaseClient.executeImportRequestsAsBulk(
        "Variable updates",
        importBulks,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
