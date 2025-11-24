/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformation;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableConditional;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableError;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEscalation;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableLink;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSignal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class TransformContext {

  private final Map<DirectBuffer, ExecutableProcess> processes = new HashMap<>();
  private final Map<DirectBuffer, ExecutableMessage> messages = new HashMap<>();
  private final Map<DirectBuffer, ExecutableError> errors = new HashMap<>();
  private final Map<DirectBuffer, ExecutableEscalation> escalations = new HashMap<>();
  private final Map<DirectBuffer, ExecutableLink> links = new HashMap<>();
  private final Map<DirectBuffer, ExecutableSignal> signals = new HashMap<>();
  private final Map<DirectBuffer, ExecutableConditional> conditionals = new HashMap<>();

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

  public void addEscalation(final ExecutableEscalation escalation) {
    escalations.put(escalation.getId(), escalation);
  }

  public ExecutableEscalation getEscalation(final String id) {
    return escalations.get(wrapString(id));
  }

  public void addLink(final ExecutableLink link) {
    links.put(link.getName(), link);
  }

  public ExecutableLink getLink(final String name) {
    return links.get(wrapString(name));
  }

  public void addSignal(final ExecutableSignal signal) {
    signals.put(signal.getId(), signal);
  }

  public ExecutableSignal getSignal(final String id) {
    return signals.get(wrapString(id));
  }

  public ExpressionLanguage getExpressionLanguage() {
    return expressionLanguage;
  }

  public void setExpressionLanguage(final ExpressionLanguage expressionLanguage) {
    this.expressionLanguage = expressionLanguage;
  }

  public void addConditional(final ExecutableConditional condition) {
    conditionals.put(condition.getId(), condition);
  }

  public ExecutableConditional getConditional(final String id) {
    return conditionals.get(wrapString(id));
  }
}
