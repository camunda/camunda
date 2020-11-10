/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.es.writer.activity.RunningActivityInstanceWriter;

import java.util.ArrayList;
import java.util.List;

public class RunningActivityInstanceElasticsearchImportJob extends ElasticsearchImportJob<FlowNodeEventDto> {

  private final RunningActivityInstanceWriter runningActivityInstanceWriter;
  private final CamundaEventImportService camundaEventImportService;

  public RunningActivityInstanceElasticsearchImportJob(RunningActivityInstanceWriter runningActivityInstanceWriter,
                                                       CamundaEventImportService camundaEventImportService,
                                                       Runnable callback) {
    super(callback);
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
    this.camundaEventImportService = camundaEventImportService;
  }

  @Override
  protected void persistEntities(List<FlowNodeEventDto> runningActivityInstances) {
    final List<ImportRequestDto> importBulks = new ArrayList<>();
    importBulks.addAll(runningActivityInstanceWriter.generateActivityInstanceImports(runningActivityInstances));
    importBulks.addAll(camundaEventImportService.generateRunningCamundaActivityEventsImports(runningActivityInstances));
    ElasticsearchWriterUtil.executeImportRequestsAsBulk("Running activity instances", importBulks);
  }
}
