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
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.ArrayList;
import java.util.List;

public class RunningProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private final RunningProcessInstanceWriter runningProcessInstanceWriter;
  private final CamundaEventImportService camundaEventImportService;
  private final ConfigurationService configurationService;

  public RunningProcessInstanceElasticsearchImportJob(final RunningProcessInstanceWriter runningProcessInstanceWriter,
                                                      final CamundaEventImportService camundaEventImportService,
                                                      final ConfigurationService configurationService,
                                                      final Runnable callback) {
    super(callback);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.camundaEventImportService = camundaEventImportService;
    this.configurationService = configurationService;
  }

  protected void persistEntities(List<ProcessInstanceDto> runningProcessInstances) {
    List<ImportRequestDto> importBulks = new ArrayList<>();
    importBulks.addAll(runningProcessInstanceWriter.generateProcessInstanceImports(runningProcessInstances));
    importBulks.addAll(camundaEventImportService.generateRunningProcessInstanceImports(runningProcessInstances));
    ElasticsearchWriterUtil.executeImportRequestsAsBulk(
      "Running process instances",
      importBulks,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

}
