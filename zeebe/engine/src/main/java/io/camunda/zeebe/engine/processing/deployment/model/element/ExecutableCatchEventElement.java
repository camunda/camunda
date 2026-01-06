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
import io.camunda.zeebe.util.function.TriFunction;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.agrona.DirectBuffer;

public class ExecutableCatchEventElement extends ExecutableFlowNode
    implements ExecutableCatchEvent, ExecutableCatchEventSupplier {
  private final List<ExecutableCatchEvent> events = Collections.singletonList(this);

  private ExecutableMessage message;
  private ExecutableError error;
  private ExecutableEscalation escalation;
  private ExecutableSignal signal;
  private ExecutableCompensation compensation;
  private ExecutableConditional conditional;
  private boolean interrupting;
  private TriFunction<ExpressionProcessor, Long, String, Either<Failure, Timer>> timerFactory;

  private boolean isConnectedToEventBasedGateway;

  private boolean isLink;

  private boolean isCompensation;

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
  public boolean isEscalation() {
    return escalation != null;
  }

  @Override
  public boolean isLink() {
    return isLink;
  }

  @Override
  public boolean isSignal() {
    return signal != null;
  }

  @Override
  public boolean isCompensation() {
    return isCompensation;
  }

  @Override
  public boolean isConditional() {
    return conditional != null;
  }

  @Override
  public ExecutableMessage getMessage() {
    return message;
  }

  public void setMessage(final ExecutableMessage message) {
    this.message = message;
  }

  @Override
  public TriFunction<ExpressionProcessor, Long, String, Either<Failure, Timer>> getTimerFactory() {
    return timerFactory;
  }

  public void setTimerFactory(
      final TriFunction<ExpressionProcessor, Long, String, Either<Failure, Timer>> timerFactory) {
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
  public ExecutableEscalation getEscalation() {
    return escalation;
  }

  public void setEscalation(final ExecutableEscalation escalation) {
    this.escalation = escalation;
  }

  @Override
  public ExecutableSignal getSignal() {
    return signal;
  }

  @Override
  public ExecutableConditional getConditional() {
    return conditional;
  }

  public void setConditional(final ExecutableConditional conditional) {
    this.conditional = conditional;
  }

  public void setSignal(final ExecutableSignal signal) {
    this.signal = signal;
  }

  public void setLink(final boolean isLink) {
    this.isLink = isLink;
  }

  @Override
  public List<ExecutableCatchEvent> getEvents() {
    return events;
  }

  @Override
  public Collection<DirectBuffer> getInterruptingElementIds() {
    return Collections.emptySet();
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

  public ExecutableCompensation getCompensation() {
    return compensation;
  }

  public void setCompensation(final boolean isCompensation) {
    this.isCompensation = isCompensation;
  }

  public void setCompensation(final ExecutableCompensation compensation) {
    this.compensation = compensation;
  }
}
