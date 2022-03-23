/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionXmlWriter;

import java.util.List;

public class DecisionDefinitionXmlElasticsearchImportJob extends ElasticsearchImportJob<DecisionDefinitionOptimizeDto> {

  private DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;

  public DecisionDefinitionXmlElasticsearchImportJob(final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter,
                                                     final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.decisionDefinitionXmlWriter = decisionDefinitionXmlWriter;
  }

  @Override
  protected void persistEntities(List<DecisionDefinitionOptimizeDto> newOptimizeEntities) {
    decisionDefinitionXmlWriter.importDecisionDefinitionXmls(newOptimizeEntities);
  }
}
