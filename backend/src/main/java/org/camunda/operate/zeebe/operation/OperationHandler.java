/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebe.operation;

import org.camunda.operate.entities.OperationType;
import org.camunda.operate.exceptions.PersistenceException;

public interface OperationHandler {

  void handle(String workflowInstanceId) throws PersistenceException;

  OperationType getType();

}
