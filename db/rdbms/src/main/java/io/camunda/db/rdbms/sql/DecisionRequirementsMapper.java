/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.DecisionRequirementsDbQuery;
import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.search.entities.DecisionRequirementsEntity;
import java.util.List;

public interface DecisionRequirementsMapper {

  void insert(DecisionRequirementsDbModel processDeployment);

  Long count(DecisionRequirementsDbQuery filter);

  List<DecisionRequirementsEntity> search(DecisionRequirementsDbQuery filter);

  void deleteByKeys(List<Long> decisionRequirementsKeys);
}
