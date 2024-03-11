/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import java.util.List;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;

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
