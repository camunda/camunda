/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;

import java.util.List;

public class RunningProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private RunningProcessInstanceWriter runningProcessInstanceWriter;
  private CamundaEventImportService camundaEventService;

  public RunningProcessInstanceElasticsearchImportJob(RunningProcessInstanceWriter runningProcessInstanceWriter,
                                                      CamundaEventImportService camundaEventService,
                                                      Runnable callback) {
    super(callback);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.camundaEventService = camundaEventService;
  }
  protected void persistEntities(List<ProcessInstanceDto> runningProcessInstances) throws Exception {
    runningProcessInstanceWriter.importProcessInstances(runningProcessInstances);
    camundaEventService.importRunningProcessInstances(runningProcessInstances);
  }
}
