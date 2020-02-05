/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.CamundaEventService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningActivityInstanceWriter;

import java.util.List;

public class RunningActivityInstanceElasticsearchImportJob extends ElasticsearchImportJob<FlowNodeEventDto> {

  private RunningActivityInstanceWriter runningActivityInstanceWriter;
  private CamundaEventService camundaEventService;

  public RunningActivityInstanceElasticsearchImportJob(RunningActivityInstanceWriter runningActivityInstanceWriter,
                                                       CamundaEventService camundaEventService,
                                                       Runnable callback) {

    super(callback);
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
    this.camundaEventService = camundaEventService;
  }

  @Override
  protected void persistEntities(List<FlowNodeEventDto> runningActivityInstances) throws Exception {
    runningActivityInstanceWriter.importActivityInstancesToProcessInstances(runningActivityInstances);
    camundaEventService.importRunningActivityInstancesToCamundaActivityEvents(runningActivityInstances);
  }
}
