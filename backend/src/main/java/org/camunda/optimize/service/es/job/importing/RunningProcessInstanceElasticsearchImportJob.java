/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;

import java.util.ArrayList;
import java.util.List;

public class RunningProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private RunningProcessInstanceWriter runningProcessInstanceWriter;
  private CamundaEventImportService camundaEventImportService;

  public RunningProcessInstanceElasticsearchImportJob(RunningProcessInstanceWriter runningProcessInstanceWriter,
                                                      CamundaEventImportService camundaEventImportService,
                                                      Runnable callback) {
    super(callback);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.camundaEventImportService = camundaEventImportService;
  }

  protected void persistEntities(List<ProcessInstanceDto> runningProcessInstances) {
    List<ImportRequestDto> importBulks = new ArrayList<>();
    importBulks.addAll(runningProcessInstanceWriter.generateProcessInstanceImports(runningProcessInstances));
    importBulks.addAll(camundaEventImportService.generateRunningProcessInstanceImports(runningProcessInstances));
    ElasticsearchWriterUtil.executeImportRequestsAsBulk("Running process instances", importBulks);
  }

}
