/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;

import java.util.List;

public class CompletedProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private CompletedProcessInstanceWriter completedProcessInstanceWriter;
  private CamundaEventImportService camundaEventService;

  public CompletedProcessInstanceElasticsearchImportJob(CompletedProcessInstanceWriter completedProcessInstanceWriter,
                                                        CamundaEventImportService camundaEventService,
                                                        Runnable callback) {
    super(callback);
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
    this.camundaEventService = camundaEventService;
  }

  @Override
  protected void persistEntities(List<ProcessInstanceDto> processInstances) throws Exception {
    completedProcessInstanceWriter.importProcessInstances(processInstances);
    camundaEventService.importCompletedProcessInstances(processInstances);
  }
}
