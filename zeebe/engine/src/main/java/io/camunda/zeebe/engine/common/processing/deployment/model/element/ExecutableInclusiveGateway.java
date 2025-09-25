/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.element;

import java.util.ArrayList;
import java.util.List;

public class ExecutableInclusiveGateway extends ExecutableFlowNode {

  private final List<ExecutableSequenceFlow> outgoingWithCondition = new ArrayList<>();
  private ExecutableSequenceFlow defaultFlow;

  public ExecutableInclusiveGateway(final String id) {
    super(id);
  }

  public ExecutableSequenceFlow getDefaultFlow() {
    return defaultFlow;
  }

  public void setDefaultFlow(final ExecutableSequenceFlow defaultFlow) {
    this.defaultFlow = defaultFlow;
  }

  @Override
  public void addOutgoing(final ExecutableSequenceFlow flow) {
    super.addOutgoing(flow);
    if (flow.getCondition() != null) {
      outgoingWithCondition.add(flow);
    }
  }

  public List<ExecutableSequenceFlow> getOutgoingWithCondition() {
    return outgoingWithCondition;
  }
}
