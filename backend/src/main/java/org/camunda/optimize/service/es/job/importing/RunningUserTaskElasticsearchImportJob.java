/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningUserTaskInstanceWriter;

import java.util.List;

public class RunningUserTaskElasticsearchImportJob extends ElasticsearchImportJob<UserTaskInstanceDto> {
  private RunningUserTaskInstanceWriter runningUserTaskInstanceWriter;

  public RunningUserTaskElasticsearchImportJob(final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter,
                                               Runnable callback) {
    super(callback);
    this.runningUserTaskInstanceWriter = runningUserTaskInstanceWriter;
  }

  @Override
  protected void persistEntities(List<UserTaskInstanceDto> newOptimizeEntities) throws Exception {
    runningUserTaskInstanceWriter.importUserTaskInstances(newOptimizeEntities);
  }
}

