/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.CamundaActivityEventService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;

import java.util.List;

public class RunningProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private RunningProcessInstanceWriter runningProcessInstanceWriter;
  private CamundaActivityEventService camundaActivityEventService;

  public RunningProcessInstanceElasticsearchImportJob(RunningProcessInstanceWriter runningProcessInstanceWriter,
                                                      CamundaActivityEventService camundaActivityEventService,
                                                      Runnable callback) {
    super(callback);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.camundaActivityEventService = camundaActivityEventService;
  }
  protected void persistEntities(List<ProcessInstanceDto> runningProcessInstances) throws Exception {
    runningProcessInstanceWriter.importProcessInstances(runningProcessInstances);
    camundaActivityEventService.importRunningProcessInstancesToCamundaActivityEvents(runningProcessInstances);
  }
}
