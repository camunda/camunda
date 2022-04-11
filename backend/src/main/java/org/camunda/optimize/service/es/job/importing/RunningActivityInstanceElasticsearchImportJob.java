/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.es.writer.activity.RunningActivityInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.ArrayList;
import java.util.List;

public class RunningActivityInstanceElasticsearchImportJob extends ElasticsearchImportJob<FlowNodeEventDto> {

  private final RunningActivityInstanceWriter runningActivityInstanceWriter;
  private final CamundaEventImportService camundaEventImportService;
  private final ConfigurationService configurationService;

  public RunningActivityInstanceElasticsearchImportJob(final RunningActivityInstanceWriter runningActivityInstanceWriter,
                                                       final CamundaEventImportService camundaEventImportService,
                                                       final ConfigurationService configurationService,
                                                       final Runnable callback) {
    super(callback);
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
    this.camundaEventImportService = camundaEventImportService;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<FlowNodeEventDto> runningActivityInstances) {
    final List<ImportRequestDto> importBulks = new ArrayList<>();
    importBulks.addAll(runningActivityInstanceWriter.generateActivityInstanceImports(runningActivityInstances));
    importBulks.addAll(camundaEventImportService.generateRunningCamundaActivityEventsImports(runningActivityInstances));
    ElasticsearchWriterUtil.executeImportRequestsAsBulk(
      "Running activity instances",
      importBulks,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }
}
