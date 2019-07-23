/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.msgpack.mapping.Mapping;
import java.util.ArrayList;
import java.util.List;

public class ExecutableFlowNode extends AbstractFlowElement {

  private final List<ExecutableSequenceFlow> incoming = new ArrayList<>();
  private final List<ExecutableSequenceFlow> outgoing = new ArrayList<>();

  private Mapping[] inputMappings = new Mapping[0];
  private Mapping[] outputMappings = new Mapping[0];

  public ExecutableFlowNode(String id) {
    super(id);
  }

  public List<ExecutableSequenceFlow> getOutgoing() {
    return outgoing;
  }

  public void addOutgoing(ExecutableSequenceFlow flow) {
    this.outgoing.add(flow);
  }

  public List<ExecutableSequenceFlow> getIncoming() {
    return incoming;
  }

  public void addIncoming(ExecutableSequenceFlow flow) {
    this.incoming.add(flow);
  }

  public Mapping[] getInputMappings() {
    return inputMappings;
  }

  public void setInputMappings(Mapping[] inputMappings) {
    this.inputMappings = inputMappings;
  }

  public Mapping[] getOutputMappings() {
    return outputMappings;
  }

  public void setOutputMappings(Mapping[] outputMappings) {
    this.outputMappings = outputMappings;
  }
}
