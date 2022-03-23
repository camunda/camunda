/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.es.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;

public class ZeebeProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private final ZeebeProcessInstanceWriter zeebeProcessInstanceWriter;
  private final ConfigurationService configurationService;

  public ZeebeProcessInstanceElasticsearchImportJob(final ZeebeProcessInstanceWriter zeebeProcessInstanceWriter,
                                                    final ConfigurationService configurationService,
                                                    final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.zeebeProcessInstanceWriter = zeebeProcessInstanceWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<ProcessInstanceDto> completedProcessInstances) {
    final List<ImportRequestDto> importRequests =
      zeebeProcessInstanceWriter.generateProcessInstanceImports(newOptimizeEntities);
    ElasticsearchWriterUtil.executeImportRequestsAsBulk(
      "Zeebe process instances",
      importRequests,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }
}
