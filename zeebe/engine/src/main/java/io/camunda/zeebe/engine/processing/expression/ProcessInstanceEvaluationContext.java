/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import java.util.stream.Stream;

public class ProcessInstanceEvaluationContext implements ScopedEvaluationContext {

  private static final long UNSET_KEY = -1;
  final long scopeKey;
  final ElementInstanceState elementInstanceState;

  public ProcessInstanceEvaluationContext(final ElementInstanceState elementInstanceState) {
    scopeKey = UNSET_KEY;
    this.elementInstanceState = elementInstanceState;
  }

  public ProcessInstanceEvaluationContext(
      final long scopeKey, final ElementInstanceState elementInstanceState) {
    this.scopeKey = scopeKey;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public Object getVariable(final String variableName) {
    if (scopeKey == UNSET_KEY) {
      return null;
    }
    return switch (variableName) {
      case "key" -> getRecord().getProcessInstanceKey();
      case "elementType" -> getRecord().getBpmnElementType();
      default -> null;
    };
  }

  @Override
  public Stream<String> getVariables() {
    return Stream.of("key", "elementType");
  }

  private ProcessInstanceRecord getRecord() {
    return elementInstanceState.getInstance(scopeKey).getValue();
  }

  @Override
  public ScopedEvaluationContext scoped(final long scopeKey) {
    return new ProcessInstanceEvaluationContext(scopeKey, elementInstanceState);
  }
}
