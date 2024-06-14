/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.usertask.RunningUserTaskInstanceWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public class RunningUserTaskDatabaseImportJob extends DatabaseImportJob<FlowNodeInstanceDto> {

  private final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter;
  private final ConfigurationService configurationService;

  public RunningUserTaskDatabaseImportJob(
      final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter,
      final ConfigurationService configurationService,
      final Runnable callback,
      final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.runningUserTaskInstanceWriter = runningUserTaskInstanceWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<FlowNodeInstanceDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests =
        runningUserTaskInstanceWriter.generateUserTaskImports(newOptimizeEntities);
    databaseClient.executeImportRequestsAsBulk(
        "Running user tasks",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
