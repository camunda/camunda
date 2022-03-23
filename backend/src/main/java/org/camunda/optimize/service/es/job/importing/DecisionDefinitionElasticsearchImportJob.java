/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;

import java.util.List;

public class DecisionDefinitionElasticsearchImportJob extends ElasticsearchImportJob<DecisionDefinitionOptimizeDto> {

  private DecisionDefinitionWriter decisionDefinitionWriter;

  public DecisionDefinitionElasticsearchImportJob(final DecisionDefinitionWriter decisionDefinitionWriter,
                                                  final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.decisionDefinitionWriter = decisionDefinitionWriter;
  }

  @Override
  protected void persistEntities(List<DecisionDefinitionOptimizeDto> newOptimizeEntities) {
    decisionDefinitionWriter.importDecisionDefinitions(newOptimizeEntities);
  }
}
