/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.DecisionDefinitionWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import java.util.List;

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
