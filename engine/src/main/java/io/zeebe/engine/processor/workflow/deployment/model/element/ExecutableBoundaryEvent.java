/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

public class ExecutableBoundaryEvent extends ExecutableCatchEventElement {
  private boolean cancelActivity;

  public ExecutableBoundaryEvent(String id) {
    super(id);
  }

  public boolean cancelActivity() {
    return cancelActivity;
  }

  @Override
  public boolean shouldCloseMessageSubscriptionOnCorrelate() {
    return cancelActivity;
  }

  public void setCancelActivity(boolean cancelActivity) {
    this.cancelActivity = cancelActivity;
  }
}
