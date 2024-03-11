/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import java.util.ArrayList;
import java.util.List;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

public class RunningProcessInstanceDatabaseImportJob extends DatabaseImportJob<ProcessInstanceDto> {

  private final RunningProcessInstanceWriter runningProcessInstanceWriter;
  private final CamundaEventImportService camundaEventImportService;
  private final ConfigurationService configurationService;

  public RunningProcessInstanceDatabaseImportJob(
      final RunningProcessInstanceWriter runningProcessInstanceWriter,
      final CamundaEventImportService camundaEventImportService,
      final ConfigurationService configurationService,
      final Runnable callback,
      final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.camundaEventImportService = camundaEventImportService;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<ProcessInstanceDto> runningProcessInstances) {
    final List<ImportRequestDto> importBulks = new ArrayList<>();
    importBulks.addAll(
        runningProcessInstanceWriter.generateProcessInstanceImports(runningProcessInstances));
    importBulks.addAll(
        camundaEventImportService.generateRunningProcessInstanceImports(runningProcessInstances));
    databaseClient.executeImportRequestsAsBulk(
        "Running process instances",
        importBulks,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
