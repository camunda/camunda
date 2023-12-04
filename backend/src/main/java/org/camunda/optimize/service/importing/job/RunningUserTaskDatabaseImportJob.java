/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.writer.usertask.RunningUserTaskInstanceWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;

public class RunningUserTaskDatabaseImportJob extends DatabaseImportJob<FlowNodeInstanceDto> {

  private final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter;
  private final ConfigurationService configurationService;

  public RunningUserTaskDatabaseImportJob(final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter,
                                          final ConfigurationService configurationService,
                                          final Runnable callback) {
    super(callback);
    this.runningUserTaskInstanceWriter = runningUserTaskInstanceWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<FlowNodeInstanceDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests = runningUserTaskInstanceWriter.generateUserTaskImports(
      newOptimizeEntities);
    //todo handle it in the OPT-7228
    ElasticsearchWriterUtil.executeImportRequestsAsBulk(
      "Running user tasks",
      importRequests,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

}

