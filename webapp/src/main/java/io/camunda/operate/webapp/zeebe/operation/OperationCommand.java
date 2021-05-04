/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.zeebe.operation;

import io.camunda.operate.entities.OperationEntity;

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
