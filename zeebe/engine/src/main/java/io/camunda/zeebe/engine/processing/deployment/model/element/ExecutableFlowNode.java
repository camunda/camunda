/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.protocol.record.value.ExecutionListenerEventType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExecutableFlowNode extends AbstractFlowElement {

  private final List<ExecutableSequenceFlow> incoming = new ArrayList<>();
  private final List<ExecutableSequenceFlow> outgoing = new ArrayList<>();

  private Optional<Expression> inputMappings = Optional.empty();
  private Optional<Expression> outputMappings = Optional.empty();

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

  public void addListener(
      final ExecutionListenerEventType eventType, final Expression type, final Expression retries) {
    final ExecutionListener listener = new ExecutionListener();
    listener.setEventType(eventType);

    final JobWorkerProperties jobWorkerProperties = new JobWorkerProperties();
    jobWorkerProperties.setType(type);
    jobWorkerProperties.setRetries(retries);
    listener.setJobWorkerProperties(jobWorkerProperties);
    executionListeners.add(listener);
  }

  public boolean hasExecutionListeners() {
    return !executionListeners.isEmpty();
  }

  public List<ExecutionListener> getStartExecutionListeners() {
    return executionListeners.stream()
        .filter(el -> el.getEventType() == ExecutionListenerEventType.START)
        .toList();
  }

  public List<ExecutionListener> getEndExecutionListeners() {
    return executionListeners.stream()
        .filter(el -> el.getEventType() == ExecutionListenerEventType.END)
        .toList();
  }
}
