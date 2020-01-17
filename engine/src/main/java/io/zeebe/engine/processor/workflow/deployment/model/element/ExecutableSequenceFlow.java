/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.msgpack.el.CompiledJsonCondition;

public class ExecutableSequenceFlow extends AbstractFlowElement {

  private ExecutableFlowNode source;
  private ExecutableFlowNode target;
  private CompiledJsonCondition condition;

  public ExecutableSequenceFlow(final String id) {
    super(id);
  }

  public ExecutableFlowNode getTarget() {
    return target;
  }

  public void setTarget(final ExecutableFlowNode target) {
    this.target = target;
  }

  public ExecutableFlowNode getSource() {
    return source;
  }

  public void setSource(final ExecutableFlowNode source) {
    this.source = source;
  }

  public CompiledJsonCondition getCondition() {
    return condition;
  }

  public void setCondition(final CompiledJsonCondition condition) {
    this.condition = condition;
  }
}
