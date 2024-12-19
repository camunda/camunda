/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import io.camunda.client.CamundaClient;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.Set;

public interface OperationHandler {

  void handle(OperationEntity operation);

  void handleWithException(OperationEntity operation) throws Exception;

  Set<OperationType> getTypes();

  // Needed for tests
  void setCamundaClient(final CamundaClient camundaClient);
}
