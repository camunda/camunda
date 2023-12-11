/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.db.writer.DecisionDefinitionWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;

import java.util.List;

public class DecisionDefinitionDatabaseImportJob extends DatabaseImportJob<DecisionDefinitionOptimizeDto> {

  private final DecisionDefinitionWriter decisionDefinitionWriter;

  public DecisionDefinitionDatabaseImportJob(final DecisionDefinitionWriter decisionDefinitionWriter,
                                             final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.decisionDefinitionWriter = decisionDefinitionWriter;
  }

  @Override
  protected void persistEntities(List<DecisionDefinitionOptimizeDto> newOptimizeEntities) {
    decisionDefinitionWriter.importDecisionDefinitions(newOptimizeEntities);
  }

}
