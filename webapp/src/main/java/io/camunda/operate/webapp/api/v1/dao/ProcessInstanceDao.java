/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.webapp.api.v1.entities.ChangeStatus;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;

public interface ProcessInstanceDao extends
    SearchableDao<ProcessInstance>,
    SortableDao<ProcessInstance>,
    PageableDao<ProcessInstance> {

  ProcessInstance byKey(Long key) throws APIException;

  ChangeStatus delete(Long key) throws APIException;

}
