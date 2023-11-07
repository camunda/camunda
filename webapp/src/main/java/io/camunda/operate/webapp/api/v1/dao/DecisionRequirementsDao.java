/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;

import java.util.List;
import java.util.Set;

public interface DecisionRequirementsDao extends
    SearchableDao<DecisionRequirements> {

  DecisionRequirements byKey(Long key) throws APIException;

  List<DecisionRequirements> byKeys(Set<Long> keys) throws APIException;

  String xmlByKey(Long key) throws APIException;
}
