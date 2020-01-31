/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.msgpack.mapping.Mappings;
import java.util.ArrayList;
import java.util.List;

public class ExecutableFlowNode extends AbstractFlowElement {

  private final List<ExecutableSequenceFlow> incoming = new ArrayList<>();
  private final List<ExecutableSequenceFlow> outgoing = new ArrayList<>();

  private Mappings inputMappings = new Mappings();
  private Mappings outputMappings = new Mappings();

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

  public Mappings getInputMappings() {
    return inputMappings;
  }

  public void setInputMappings(final Mappings inputMappings) {
    this.inputMappings = inputMappings;
  }

  public Mappings getOutputMappings() {
    return outputMappings;
  }

  public void setOutputMappings(final Mappings outputMappings) {
    this.outputMappings = outputMappings;
  }
}
