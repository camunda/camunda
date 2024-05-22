/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import java.util.List;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.DecisionDefinitionWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;

public class DecisionDefinitionDatabaseImportJob
    extends DatabaseImportJob<DecisionDefinitionOptimizeDto> {

  private final DecisionDefinitionWriter decisionDefinitionWriter;

  public DecisionDefinitionDatabaseImportJob(
      final DecisionDefinitionWriter decisionDefinitionWriter,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.decisionDefinitionWriter = decisionDefinitionWriter;
  }

  @Override
  protected void persistEntities(List<DecisionDefinitionOptimizeDto> newOptimizeEntities) {
    decisionDefinitionWriter.importDecisionDefinitions(newOptimizeEntities);
  }
}
