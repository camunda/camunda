/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;

import java.util.List;

public class DecisionInstanceElasticsearchImportJob extends ElasticsearchImportJob<DecisionInstanceDto> {

  private DecisionInstanceWriter decisionInstanceWriter;

  public DecisionInstanceElasticsearchImportJob(DecisionInstanceWriter decisionInstanceWriter, Runnable callback) {
    super(callback);
    this.decisionInstanceWriter = decisionInstanceWriter;
  }

  @Override
  protected void persistEntities(List<DecisionInstanceDto> newOptimizeEntities) throws Exception {
    decisionInstanceWriter.importDecisionInstances(newOptimizeEntities);
  }
}
