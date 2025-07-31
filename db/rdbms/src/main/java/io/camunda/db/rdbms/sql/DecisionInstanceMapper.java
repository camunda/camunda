/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.DecisionInstanceDbQuery;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.search.entities.DecisionInstanceEntity;
import java.util.List;

public interface DecisionInstanceMapper extends ProcessBasedHistoryCleanupMapper {

  void insert(DecisionInstanceDbModel decisionInstance);

  Long count(DecisionInstanceDbQuery filter);

  List<DecisionInstanceEntity> search(DecisionInstanceDbQuery filter);

  List<DecisionInstanceDbModel.EvaluatedInput> loadInputs(List<String> decisionInstanceIds);

  List<DecisionInstanceDbModel.EvaluatedOutput> loadOutputs(List<String> decisionInstanceIds);
}
