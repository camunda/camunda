/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.model.bpmn.util.time.Timer;
import java.util.function.BiFunction;

public class ExecutableReceiveTask extends ExecutableActivity implements ExecutableCatchEvent {

  private ExecutableMessage message;

  public ExecutableReceiveTask(final String id) {
    super(id);

    getEvents().add(this);
    getInterruptingElementIds().add(getId());
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
  public boolean isError() {
    return false;
  }

  @Override
  public ExecutableMessage getMessage() {
    return message;
  }

  @Override
  public BiFunction<ExpressionProcessor, Long, Timer> getTimerFactory() {
    return (expressionProcessor, context) -> null;
  }

  @Override
  public ExecutableError getError() {
    return null;
  }

  public void setMessage(final ExecutableMessage message) {
    this.message = message;
  }
}
