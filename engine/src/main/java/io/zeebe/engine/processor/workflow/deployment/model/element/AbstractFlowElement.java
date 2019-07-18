/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.util.buffer.BufferUtil;
import java.util.EnumMap;
import java.util.Map;
import org.agrona.DirectBuffer;

public abstract class AbstractFlowElement implements ExecutableFlowElement {

  private final DirectBuffer id;
  private final Map<WorkflowInstanceIntent, BpmnStep> bpmnSteps =
      new EnumMap<>(WorkflowInstanceIntent.class);
  private BpmnElementType elementType;

  public AbstractFlowElement(String id) {
    this.id = BufferUtil.wrapString(id);
    this.elementType = BpmnElementType.UNSPECIFIED;
  }

  @Override
  public DirectBuffer getId() {
    return id;
  }

  @Override
  public BpmnStep getStep(WorkflowInstanceIntent state) {
    return bpmnSteps.get(state);
  }

  @Override
  public BpmnElementType getElementType() {
    return elementType;
  }

  public void setElementType(BpmnElementType elementType) {
    this.elementType = elementType;
  }

  public void bindLifecycleState(WorkflowInstanceIntent state, BpmnStep step) {
    this.bpmnSteps.put(state, step);
  }
}
