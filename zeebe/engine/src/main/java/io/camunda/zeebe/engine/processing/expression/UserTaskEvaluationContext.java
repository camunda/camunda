/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.Optional;
import java.util.stream.Stream;

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
  public Stream<String> getVariables() {
    return Stream.of("assignee");
  }

  @Override
  public ScopedEvaluationContext scoped(final long scopeKey) {
    return new UserTaskEvaluationContext(processingState, scopeKey);
  }

  private Optional<Object> resolveVariable(final String variableName) {
    return switch (variableName) {
      case "assignee" -> findUserTask().map(UserTaskRecord::getAssignee);
      case "priority" -> findUserTask().map(UserTaskRecord::getPriority);
      case "formKey" -> findUserTask().map(UserTaskRecord::getFormKey);
      default -> Optional.empty();
    };
  }

  private Optional<UserTaskRecord> findUserTask() {
    final var userTask = processingState.getElementInstanceState().getInstance(scopeKey);
    final var userTaskKey = userTask.getUserTaskKey();
    if (userTaskKey < 0L) {
      return Optional.empty();
    } else {
      return Optional.of(processingState.getUserTaskState().getUserTask(userTaskKey));
    }
  }
}
