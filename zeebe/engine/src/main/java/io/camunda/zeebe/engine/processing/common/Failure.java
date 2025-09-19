/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import java.util.Objects;

/** Simple String wrapper for when something fails and a message needs to be used. */
public final class Failure {

  private final String message;
  private final ErrorType errorType;
  private final long variableScopeKey;
  private BpmnElementContext context;

  public Failure(final String message) {
    this.message = message;
    errorType = null;
    variableScopeKey = -1L;
  }

  public Failure(final String message, final ErrorType errorType) {
    this.message = message;
    this.errorType = errorType;
    variableScopeKey = -1L;
  }

  public Failure(final String message, final ErrorType errorType, final long variableScopeKey) {
    this.message = message;
    this.errorType = errorType;
    this.variableScopeKey = variableScopeKey;
  }

  public String getMessage() {
    return message;
  }

  public ErrorType getErrorType() {
    return errorType;
  }

  public long getVariableScopeKey() {
    return variableScopeKey;
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, errorType, variableScopeKey);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Failure failure = (Failure) o;
    return variableScopeKey == failure.variableScopeKey
        && Objects.equals(message, failure.message)
        && errorType == failure.errorType;
  }

  @Override
  public String toString() {
    return "Failure{"
        + "message='"
        + message
        + '\''
        + ", errorType="
        + errorType
        + ", variableScopeKey="
        + variableScopeKey
        + '}';
  }

  public BpmnElementContext getContext() {
    return context;
  }

  public Failure setContext(final BpmnElementContext context) {
    this.context = context;
    return this;
  }
}
