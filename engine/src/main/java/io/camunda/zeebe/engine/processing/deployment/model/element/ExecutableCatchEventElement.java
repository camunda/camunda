/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import io.camunda.zeebe.util.Either;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import org.agrona.DirectBuffer;

public class ExecutableCatchEventElement extends ExecutableFlowNode
    implements ExecutableCatchEvent, ExecutableCatchEventSupplier {
  private final List<ExecutableCatchEvent> events = Collections.singletonList(this);

  private ExecutableMessage message;
  private ExecutableError error;
  private boolean interrupting;
  private BiFunction<ExpressionProcessor, Long, Either<Failure, Timer>> timerFactory;

  private boolean isConnectedToEventBasedGateway;

  public ExecutableCatchEventElement(final String id) {
    super(id);
  }

  @Override
  public boolean isTimer() {
    return timerFactory != null;
  }

  @Override
  public boolean isMessage() {
    return message != null;
  }

  @Override
  public boolean isError() {
    return error != null;
  }

  @Override
  public ExecutableMessage getMessage() {
    return message;
  }

  public void setMessage(final ExecutableMessage message) {
    this.message = message;
  }

  @Override
  public BiFunction<ExpressionProcessor, Long, Either<Failure, Timer>> getTimerFactory() {
    return timerFactory;
  }

  public void setTimerFactory(
      final BiFunction<ExpressionProcessor, Long, Either<Failure, Timer>> timerFactory) {
    this.timerFactory = timerFactory;
  }

  @Override
  public ExecutableError getError() {
    return error;
  }

  public void setError(final ExecutableError error) {
    this.error = error;
  }

  @Override
  public List<ExecutableCatchEvent> getEvents() {
    return events;
  }

  @Override
  public Collection<DirectBuffer> getInterruptingElementIds() {
    return Collections.singleton(getId());
  }

  @Override
  public Collection<DirectBuffer> getBoundaryElementIds() {
    return Collections.emptySet();
  }

  public boolean interrupting() {
    return interrupting;
  }

  public void setInterrupting(final boolean interrupting) {
    this.interrupting = interrupting;
  }

  public boolean isConnectedToEventBasedGateway() {
    return isConnectedToEventBasedGateway;
  }

  public void setConnectedToEventBasedGateway(final boolean connectedToEventBasedGateway) {
    isConnectedToEventBasedGateway = connectedToEventBasedGateway;
  }
}
