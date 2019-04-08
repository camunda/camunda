/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;

import java.util.List;

public class DecisionDefinitionElasticsearchImportJob extends ElasticsearchImportJob<DecisionDefinitionOptimizeDto> {

  private DecisionDefinitionWriter decisionDefinitionWriter;

  public DecisionDefinitionElasticsearchImportJob(DecisionDefinitionWriter decisionDefinitionWriter) {
    super(() -> {});
    this.decisionDefinitionWriter = decisionDefinitionWriter;
  }

  @Override
  protected void persistEntities(List<DecisionDefinitionOptimizeDto> newOptimizeEntities) throws Exception {
    decisionDefinitionWriter.importProcessDefinitions(newOptimizeEntities);
  }
}
