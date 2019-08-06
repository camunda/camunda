/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.model.bpmn.util.time.RepeatingInterval;

public class ExecutableReceiveTask extends ExecutableActivity implements ExecutableCatchEvent {

  private ExecutableMessage message;

  public ExecutableReceiveTask(String id) {
    super(id);

    getEvents().add(this);
    getInterruptingElementIds().add(this.getId());
  }

  @Override
  public boolean isTimer() {
    return false;
  }

  @Override
  public boolean isMessage() {
    return true;
  }

  @Override
  public ExecutableMessage getMessage() {
    return message;
  }

  @Override
  public RepeatingInterval getTimer() {
    return null;
  }

  public void setMessage(ExecutableMessage message) {
    this.message = message;
  }
}
