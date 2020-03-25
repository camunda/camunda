/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;

import java.util.List;

public class UserOperationLogElasticsearchImportJob extends ElasticsearchImportJob<UserOperationLogEntryDto> {
  private RunningProcessInstanceWriter runningProcessInstanceWriter;

  public UserOperationLogElasticsearchImportJob(final RunningProcessInstanceWriter runningProcessInstanceWriter,
                                                Runnable callback) {
    super(callback);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
  }

  @Override
  protected void persistEntities(final List<UserOperationLogEntryDto> newOptimizeEntities) throws Exception {
    runningProcessInstanceWriter.importProcessInstancesFromUserOperationLogs(newOptimizeEntities);
  }
}
