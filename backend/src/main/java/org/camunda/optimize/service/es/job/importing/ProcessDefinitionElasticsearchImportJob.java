/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;

import java.util.List;

public class ProcessDefinitionElasticsearchImportJob extends ElasticsearchImportJob<ProcessDefinitionOptimizeDto> {

  private ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionElasticsearchImportJob(final ProcessDefinitionWriter processDefinitionWriter,
                                                 final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  protected void persistEntities(List<ProcessDefinitionOptimizeDto> newOptimizeEntities) {
    processDefinitionWriter.importProcessDefinitions(newOptimizeEntities);
  }
}
