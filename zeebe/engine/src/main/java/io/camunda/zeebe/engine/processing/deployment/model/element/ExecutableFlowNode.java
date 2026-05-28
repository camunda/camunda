/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ExecutableFlowNode extends AbstractFlowElement {

  private final List<ExecutableSequenceFlow> incoming = new ArrayList<>();
  private final List<ExecutableSequenceFlow> outgoing = new ArrayList<>();

  private Optional<Expression> inputMappings = Optional.empty();
  private Optional<Expression> outputMappings = Optional.empty();

  /**
   * Secret references statically declared in this node's input mappings, indexed by the
   * JSON-pointer path (e.g. {@code /authentication/token}) of the variable they produce. The value
   * is the set of {@code camunda.secrets.*} references found at that path. Computed at deploy time
   * so the gateway can resolve them without re-parsing variables. Empty when no input mapping
   * references a secret.
   */
  private Map<String, Set<String>> inputSecretReferences = Map.of();

  private final List<ExecutionListener> executionListeners = new ArrayList<>();

  public ExecutableFlowNode(final String id) {
    super(id);
  }

  public List<ExecutableSequenceFlow> getOutgoing() {
    return outgoing;
  }

  public void addOutgoing(final ExecutableSequenceFlow flow) {
    outgoing.add(flow);
  }

  public List<ExecutableSequenceFlow> getIncoming() {
    return incoming;
  }

  public void addIncoming(final ExecutableSequenceFlow flow) {
    incoming.add(flow);
  }

  public Optional<Expression> getInputMappings() {
    return inputMappings;
  }

  public void setInputMappings(final Expression inputMappings) {
    this.inputMappings = Optional.of(inputMappings);
  }

  public Optional<Expression> getOutputMappings() {
    return outputMappings;
  }

  public void setOutputMappings(final Expression outputMappings) {
    this.outputMappings = Optional.of(outputMappings);
  }

  public Map<String, Set<String>> getInputSecretReferences() {
    return inputSecretReferences;
  }

  public void setInputSecretReferences(final Map<String, Set<String>> inputSecretReferences) {
    this.inputSecretReferences = inputSecretReferences;
  }

  public List<ExecutionListener> getBeforeAllExecutionListeners() {
    return executionListeners.stream()
        .filter(el -> el.getEventType() == ZeebeExecutionListenerEventType.beforeAll)
        .toList();
  }

  public List<ExecutionListener> getStartExecutionListeners() {
    return executionListeners.stream()
        .filter(el -> el.getEventType() == ZeebeExecutionListenerEventType.start)
        .toList();
  }

  public List<ExecutionListener> getEndExecutionListeners() {
    return executionListeners.stream()
        .filter(el -> el.getEventType() == ZeebeExecutionListenerEventType.end)
        .toList();
  }

  public List<ExecutionListener> getCancelExecutionListeners() {
    return executionListeners.stream()
        .filter(el -> el.getEventType() == ZeebeExecutionListenerEventType.cancel)
        .toList();
  }

  public boolean hasCancelExecutionListeners() {
    return executionListeners.stream()
        .anyMatch(el -> el.getEventType() == ZeebeExecutionListenerEventType.cancel);
  }

  public boolean hasExecutionListeners() {
    return !executionListeners.isEmpty();
  }

  public void addExecutionListener(
      final ZeebeExecutionListenerEventType eventType,
      final Expression type,
      final Expression retries,
      final Map<String, String> taskHeaders) {
    final ExecutionListener listener = new ExecutionListener();
    listener.setEventType(eventType);

    final JobWorkerProperties jobWorkerProperties = new JobWorkerProperties();
    jobWorkerProperties.setType(type);
    jobWorkerProperties.setRetries(retries);
    jobWorkerProperties.setTaskHeaders(taskHeaders);
    listener.setJobWorkerProperties(jobWorkerProperties);
    executionListeners.add(listener);
  }

  public void addExecutionListener(final ExecutionListener listener) {
    executionListeners.add(listener);
  }

  /**
   * Removes all {@code beforeAll} execution listeners from this flow node and returns them. Used
   * during multi-instance body transformation to re-attach them to the body.
   */
  public List<ExecutionListener> removeBeforeAllExecutionListeners() {
    final List<ExecutionListener> removed = getBeforeAllExecutionListeners();
    executionListeners.removeAll(removed);
    return removed;
  }
}
