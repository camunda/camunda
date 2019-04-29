/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;

import java.util.List;

public class ProcessDefinitionElasticsearchImportJob extends ElasticsearchImportJob<ProcessDefinitionOptimizeDto> {

  private ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionElasticsearchImportJob(ProcessDefinitionWriter processDefinitionWriter) {
    super();
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  protected void persistEntities(List<ProcessDefinitionOptimizeDto> newOptimizeEntities) throws Exception {
    processDefinitionWriter.importProcessDefinitions(newOptimizeEntities);
  }
}
