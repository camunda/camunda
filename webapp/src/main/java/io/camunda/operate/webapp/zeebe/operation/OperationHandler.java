/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.zeebe.operation;

import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;

public interface OperationHandler {

  void handle(OperationEntity operation);

  void handleWithException(OperationEntity operation) throws Exception;

  OperationType getType();

}
