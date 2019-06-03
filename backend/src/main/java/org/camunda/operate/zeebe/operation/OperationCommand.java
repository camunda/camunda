package org.camunda.operate.zeebe.operation;

import org.camunda.operate.entities.OperationEntity;

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
