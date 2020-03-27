/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class UserOperationLogElasticsearchImportJob extends ElasticsearchImportJob<UserOperationLogEntryDto> {
  private RunningProcessInstanceWriter runningProcessInstanceWriter;

  public UserOperationLogElasticsearchImportJob(final RunningProcessInstanceWriter runningProcessInstanceWriter,
                                                Runnable callback) {
    super(callback);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
  }

  @Override
  protected void persistEntities(final List<UserOperationLogEntryDto> newOptimizeEntities) throws Exception {
    runningProcessInstanceWriter.importProcessInstancesFromUserOperationLogs(
      mapUserOperationsLogsToProcessInstanceDtos(newOptimizeEntities)
    );
  }

  private List<ProcessInstanceDto> mapUserOperationsLogsToProcessInstanceDtos(
    final List<UserOperationLogEntryDto> userOperationLogEntryDtos) {
    return userOperationLogEntryDtos.stream()
      .map(userOpLog -> ProcessInstanceDto.builder()
        .processInstanceId(userOpLog.getProcessInstanceId())
        .state(userOpLog.getNewValue())
        .build()
      )
      .collect(toList());
  }
}
