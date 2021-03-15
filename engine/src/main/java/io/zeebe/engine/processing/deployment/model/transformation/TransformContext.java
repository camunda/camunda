/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.transformation;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.el.ExpressionLanguage;
import io.zeebe.engine.processing.deployment.model.element.ExecutableError;
import io.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class TransformContext {

  private final Map<DirectBuffer, ExecutableProcess> processes = new HashMap<>();
  private final Map<DirectBuffer, ExecutableMessage> messages = new HashMap<>();
  private final Map<DirectBuffer, ExecutableError> errors = new HashMap<>();

  private ExpressionLanguage expressionLanguage;

  /*
   * set whenever parsing a process
   */
  private ExecutableProcess currentProcess;

  public ExecutableProcess getCurrentProcess() {
    return currentProcess;
  }

  public void setCurrentProcess(final ExecutableProcess currentProcess) {
    this.currentProcess = currentProcess;
  }

  public void addProcess(final ExecutableProcess process) {
    processes.put(process.getId(), process);
  }

  public ExecutableProcess getProcess(final String id) {
    return processes.get(wrapString(id));
  }

  public List<ExecutableProcess> getProcesses() {
    return new ArrayList<>(processes.values());
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
