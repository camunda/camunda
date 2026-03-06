/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.query.process.FlatUserTaskDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.UserTaskWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public class FlatUserTaskDatabaseImportJob extends DatabaseImportJob<FlatUserTaskDto> {

  private final UserTaskWriter userTaskWriter;
  private final ConfigurationService configurationService;

  public FlatUserTaskDatabaseImportJob(
      final UserTaskWriter userTaskWriter,
      final ConfigurationService configurationService,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.userTaskWriter = userTaskWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<FlatUserTaskDto> userTasks) {
    final List<ImportRequestDto> importRequests =
        userTaskWriter.generateFlatUserTaskImports(userTasks);
    databaseClient.executeImportRequestsAsBulk(
        "flat user tasks",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
