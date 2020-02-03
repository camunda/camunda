/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.CamundaActivityEventService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.CompletedActivityInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;

public class CompletedActivityInstanceElasticsearchImportJob extends ElasticsearchImportJob<FlowNodeEventDto> {

  private CompletedActivityInstanceWriter completedActivityInstanceWriter;
  private CamundaActivityEventService camundaActivityEventService;
  private ConfigurationService configurationService;

  public CompletedActivityInstanceElasticsearchImportJob(CompletedActivityInstanceWriter completedActivityInstanceWriter,
                                                         CamundaActivityEventService camundaActivityEventService,
                                                         ConfigurationService configurationService,
                                                         Runnable callback) {
    super(callback);
    this.configurationService = configurationService;
    this.completedActivityInstanceWriter = completedActivityInstanceWriter;
    this.camundaActivityEventService = camundaActivityEventService;
  }

  @Override
  protected void persistEntities(List<FlowNodeEventDto> newOptimizeEntities) throws Exception {
    completedActivityInstanceWriter.importActivityInstancesToProcessInstances(newOptimizeEntities);
    if (configurationService.getEventBasedProcessConfiguration().isEnabled()) {
      camundaActivityEventService.importActivityInstancesToCamundaActivityEvents(newOptimizeEntities);
    }
  }
}
