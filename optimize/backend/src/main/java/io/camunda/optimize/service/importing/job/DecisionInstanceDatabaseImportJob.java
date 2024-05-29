/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.DecisionInstanceWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import java.util.List;

public class DecisionInstanceDatabaseImportJob extends DatabaseImportJob<DecisionInstanceDto> {

  private final DecisionInstanceWriter decisionInstanceWriter;

  public DecisionInstanceDatabaseImportJob(
      final DecisionInstanceWriter decisionInstanceWriter,
      final Runnable callback,
      final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.decisionInstanceWriter = decisionInstanceWriter;
  }

  @Override
  protected void persistEntities(List<DecisionInstanceDto> newOptimizeEntities) throws Exception {
    decisionInstanceWriter.importDecisionInstances(newOptimizeEntities);
  }
}
