/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.ArrayList;
import java.util.List;

public class CompletedProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private final CompletedProcessInstanceWriter completedProcessInstanceWriter;
  private final CamundaEventImportService camundaEventService;
  private final ConfigurationService configurationService;

  public CompletedProcessInstanceElasticsearchImportJob(final CompletedProcessInstanceWriter completedProcessInstanceWriter,
                                                        final CamundaEventImportService camundaEventService,
                                                        final ConfigurationService configurationService,
                                                        final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
    this.camundaEventService = camundaEventService;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<ProcessInstanceDto> completedProcessInstances) {
    List<ImportRequestDto> imports = new ArrayList<>();
    imports.addAll(completedProcessInstanceWriter.generateProcessInstanceImports(completedProcessInstances));
    imports.addAll(camundaEventService.generateCompletedProcessInstanceImports(completedProcessInstances));
    ElasticsearchWriterUtil.executeImportRequestsAsBulk(
      "Completed process instances",
      imports,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }
}
