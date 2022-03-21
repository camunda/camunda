/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;

public interface FlowNodeInstanceDao extends
    SearchableDao<FlowNodeInstance>,
    SortableDao<FlowNodeInstance>,
    PageableDao<FlowNodeInstance> {

  FlowNodeInstance byKey(Long key) throws APIException;
}
