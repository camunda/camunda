/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import io.camunda.webapps.schema.entities.operation.OperationEntity;

public class OperationCommand implements Runnable {

  private OperationEntity entity;

  private OperationHandler handler;

  public OperationCommand(OperationEntity entity, OperationHandler handler) {
    this.entity = entity;
    this.handler = handler;
  }

  @Override
  public void run() {
    handler.handle(entity);
  }
}
