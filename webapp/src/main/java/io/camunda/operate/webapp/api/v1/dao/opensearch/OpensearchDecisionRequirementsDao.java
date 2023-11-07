/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.webapp.api.v1.dao.DecisionRequirementsDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionRequirementsDao implements DecisionRequirementsDao {
  @Override
  public DecisionRequirements byKey(Long key) throws APIException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<DecisionRequirements> byKeys(Set<Long> keys) throws APIException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String xmlByKey(Long key) throws APIException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Results<DecisionRequirements> search(Query<DecisionRequirements> query) throws APIException {
    throw new UnsupportedOperationException();
  }
}
