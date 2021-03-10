/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.element;

import java.util.ArrayList;
import java.util.List;

public class ExecutableExclusiveGateway extends ExecutableFlowNode {

  private final List<ExecutableSequenceFlow> outgoingWithCondition = new ArrayList<>();
  private ExecutableSequenceFlow defaultFlow;

  public ExecutableExclusiveGateway(final String id) {
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
