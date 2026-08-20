/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.DecisionDefinitionDbQuery;
import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.search.entities.DecisionDefinitionEntity;
import java.util.List;

public interface DecisionDefinitionMapper {

  void insert(DecisionDefinitionDbModel processDeployment);

  Long count(DecisionDefinitionDbQuery filter);

  List<DecisionDefinitionEntity> search(DecisionDefinitionDbQuery filter);

  int deleteByDecisionRequirementsKeys(List<Long> decisionRequirementsKeys, int limit);
}
