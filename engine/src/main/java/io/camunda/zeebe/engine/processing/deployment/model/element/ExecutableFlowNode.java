/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.element;

import io.zeebe.el.Expression;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExecutableFlowNode extends AbstractFlowElement {

  private final List<ExecutableSequenceFlow> incoming = new ArrayList<>();
  private final List<ExecutableSequenceFlow> outgoing = new ArrayList<>();

  private Optional<Expression> inputMappings = Optional.empty();
  private Optional<Expression> outputMappings = Optional.empty();

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
}
