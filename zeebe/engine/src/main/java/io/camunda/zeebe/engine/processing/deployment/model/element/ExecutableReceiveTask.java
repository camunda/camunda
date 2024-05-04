/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import io.camunda.zeebe.util.Either;
import java.util.function.BiFunction;

public class ExecutableReceiveTask extends ExecutableActivity implements ExecutableCatchEvent {

  private ExecutableMessage message;

  public ExecutableReceiveTask(final String id) {
    super(id);

    getEvents().add(this);
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
  public boolean isEscalation() {
    return false;
  }

  @Override
  public boolean isLink() {
    return false;
  }

  @Override
  public boolean isSignal() {
    return false;
  }

  @Override
  public boolean isCompensation() {
    return false;
  }

  @Override
  public ExecutableMessage getMessage() {
    return message;
  }

  @Override
  public BiFunction<ExpressionProcessor, Long, Either<Failure, Timer>> getTimerFactory() {
    return (expressionProcessor, context) -> null;
  }

  @Override
  public ExecutableError getError() {
    return null;
  }

  @Override
  public ExecutableEscalation getEscalation() {
    return null;
  }

  @Override
  public ExecutableSignal getSignal() {
    return null;
  }

  public void setMessage(final ExecutableMessage message) {
    this.message = message;
  }
}
