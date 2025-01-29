/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.Optional;

public class UserTaskEvaluationContext implements ScopedEvaluationContext {

  private final ProcessingState processingState;
  private final long scopeKey;

  public UserTaskEvaluationContext(final ProcessingState processingState) {
    this.processingState = processingState;
    scopeKey = -1L;
  }

  public UserTaskEvaluationContext(final ProcessingState processingState, final long scopeKey) {
    this.processingState = processingState;
    this.scopeKey = scopeKey;
  }

  @Override
  public Object getVariable(final String variableName) {
    if (scopeKey < 0) {
      return null;
    }

    return resolveVariable(variableName).orElse(null);
  }

  @Override
  public ScopedEvaluationContext scoped(final long scopeKey) {
    return new UserTaskEvaluationContext(processingState, this.scopeKey);
  }

  private Optional<Object> resolveVariable(final String variableName) {
    switch (variableName) {
      case "assignee":
        return findUserTask().map(UserTaskRecord::getAssignee);
      case "priority":
        return findUserTask().map(UserTaskRecord::getPriority);
      case "formKey":
        return findUserTask().map(UserTaskRecord::getFormKey);
      default:
        return Optional.empty();
    }
  }

  private Optional<UserTaskRecord> findUserTask() {
    return Optional.ofNullable(scopeKey)
        .map(elementKey -> processingState.getElementInstanceState().getInstance(elementKey))
        .map(ElementInstance::getUserTaskKey)
        .map(userTaskKey -> processingState.getUserTaskState().getUserTask(userTaskKey));
  }
}
