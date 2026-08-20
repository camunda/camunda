/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  protected void persistEntities(final List<DecisionInstanceDto> newOptimizeEntities)
      throws Exception {
    decisionInstanceWriter.importDecisionInstances(newOptimizeEntities);
  }
}
