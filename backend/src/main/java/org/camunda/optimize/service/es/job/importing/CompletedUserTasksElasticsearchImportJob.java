/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.es.writer.usertask.CompletedUserTaskInstanceWriter;

import java.util.List;

public class CompletedUserTasksElasticsearchImportJob extends ElasticsearchImportJob<FlowNodeInstanceDto> {
  private CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter;

  public CompletedUserTasksElasticsearchImportJob(final CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter,
                                                  Runnable callback) {
    super(callback);
    this.completedUserTaskInstanceWriter = completedUserTaskInstanceWriter;
  }

  @Override
  protected void persistEntities(List<FlowNodeInstanceDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests = completedUserTaskInstanceWriter.generateUserTaskImports(newOptimizeEntities);
    ElasticsearchWriterUtil.executeImportRequestsAsBulk("Completed user tasks", importRequests);
  }
}
