/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebe.operation;

import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.exceptions.PersistenceException;

public interface OperationHandler {

  void handle(OperationEntity operation);

  void handleWithException(OperationEntity operation) throws PersistenceException;

  OperationType getType();

}
