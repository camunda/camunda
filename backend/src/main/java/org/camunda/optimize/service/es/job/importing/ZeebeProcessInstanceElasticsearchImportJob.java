/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.es.writer.ZeebeProcessInstanceWriter;

import java.util.List;

public class ZeebeProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private ZeebeProcessInstanceWriter zeebeProcessInstanceWriter;

  public ZeebeProcessInstanceElasticsearchImportJob(final ZeebeProcessInstanceWriter zeebeProcessInstanceWriter,
                                                    final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.zeebeProcessInstanceWriter = zeebeProcessInstanceWriter;
  }

  @Override
  protected void persistEntities(List<ProcessInstanceDto> completedProcessInstances) {
    final List<ImportRequestDto> importRequests =
      zeebeProcessInstanceWriter.generateProcessInstanceImports(newOptimizeEntities);
    ElasticsearchWriterUtil.executeImportRequestsAsBulk("Zeebe process instances", importRequests);
  }
}
