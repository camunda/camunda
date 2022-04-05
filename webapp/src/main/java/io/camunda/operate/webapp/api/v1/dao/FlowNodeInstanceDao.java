/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
