/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import io.camunda.optimize.service.CamundaEventImportService;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.activity.RunningActivityInstanceWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.List;

public class RunningActivityInstanceDatabaseImportJob extends DatabaseImportJob<FlowNodeEventDto> {

  private final RunningActivityInstanceWriter runningActivityInstanceWriter;
  private final CamundaEventImportService camundaEventImportService;
  private final ConfigurationService configurationService;

  public RunningActivityInstanceDatabaseImportJob(
      final RunningActivityInstanceWriter runningActivityInstanceWriter,
      final CamundaEventImportService camundaEventImportService,
      final ConfigurationService configurationService,
      final Runnable callback,
      final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
    this.camundaEventImportService = camundaEventImportService;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<FlowNodeEventDto> runningActivityInstances) {
    final List<ImportRequestDto> importBulks = new ArrayList<>();
    importBulks.addAll(
        runningActivityInstanceWriter.generateActivityInstanceImports(runningActivityInstances));
    importBulks.addAll(
        camundaEventImportService.generateRunningCamundaActivityEventsImports(
            runningActivityInstances));
    databaseClient.executeImportRequestsAsBulk(
        "Running activity instances",
        importBulks,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
