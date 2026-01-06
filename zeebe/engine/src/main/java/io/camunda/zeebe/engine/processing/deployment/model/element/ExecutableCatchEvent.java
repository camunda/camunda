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

public interface ExecutableCatchEvent extends ExecutableFlowElement {

  boolean isTimer();

  boolean isMessage();

  boolean isError();

  boolean isEscalation();

  boolean isLink();

  boolean isSignal();

  boolean isCompensation();

  boolean isConditional();

  default boolean isNone() {
    return !isTimer()
        && !isMessage()
        && !isError()
        && !isLink()
        && !isEscalation()
        && !isSignal()
        && !isCompensation()
        && !isConditional();
  }

  ExecutableMessage getMessage();

  default boolean isInterrupting() {
    return true;
  }

  TriFunction<ExpressionProcessor, Long, String, Either<Failure, Timer>> getTimerFactory();

  ExecutableError getError();

  ExecutableEscalation getEscalation();

  ExecutableSignal getSignal();

  ExecutableConditional getConditional();
}
