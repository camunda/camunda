/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.usertask.CompletedUserTaskInstanceWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public class CompletedUserTasksDatabaseImportJob extends DatabaseImportJob<FlowNodeInstanceDto> {

  private final CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter;
  private final ConfigurationService configurationService;

  public CompletedUserTasksDatabaseImportJob(
      final CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter,
      final ConfigurationService configurationService,
      final Runnable callback,
      final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.completedUserTaskInstanceWriter = completedUserTaskInstanceWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<FlowNodeInstanceDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests =
        completedUserTaskInstanceWriter.generateUserTaskImports(newOptimizeEntities);
    databaseClient.executeImportRequestsAsBulk(
        "Completed user tasks",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
