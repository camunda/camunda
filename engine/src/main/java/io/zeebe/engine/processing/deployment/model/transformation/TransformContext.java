/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.model.transformation;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.el.ExpressionLanguage;
import io.zeebe.engine.processing.deployment.model.element.ExecutableError;
import io.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processing.deployment.model.element.ExecutableWorkflow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class TransformContext {

  private final Map<DirectBuffer, ExecutableWorkflow> workflows = new HashMap<>();
  private final Map<DirectBuffer, ExecutableMessage> messages = new HashMap<>();
  private final Map<DirectBuffer, ExecutableError> errors = new HashMap<>();

  private ExpressionLanguage expressionLanguage;

  /*
   * set whenever parsing a workflow
   */
  private ExecutableWorkflow currentWorkflow;

  public ExecutableWorkflow getCurrentWorkflow() {
    return currentWorkflow;
  }

  public void setCurrentWorkflow(final ExecutableWorkflow currentWorkflow) {
    this.currentWorkflow = currentWorkflow;
  }

  public void addWorkflow(final ExecutableWorkflow workflow) {
    workflows.put(workflow.getId(), workflow);
  }

  public ExecutableWorkflow getWorkflow(final String id) {
    return workflows.get(wrapString(id));
  }

  public List<ExecutableWorkflow> getWorkflows() {
    return new ArrayList<>(workflows.values());
  }

  public void addMessage(final ExecutableMessage message) {
    messages.put(message.getId(), message);
  }

  public ExecutableMessage getMessage(final String id) {
    return messages.get(wrapString(id));
  }

  public void addError(final ExecutableError error) {
    errors.put(error.getId(), error);
  }

  public ExecutableError getError(final String id) {
    return errors.get(wrapString(id));
  }

  public ExpressionLanguage getExpressionLanguage() {
    return expressionLanguage;
  }

  public void setExpressionLanguage(final ExpressionLanguage expressionLanguage) {
    this.expressionLanguage = expressionLanguage;
  }
}
