/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public abstract class AbstractFlowElement implements ExecutableFlowElement {

  private final DirectBuffer id;
  private BpmnElementType elementType;
  private ExecutableFlowElement flowScope;

  public AbstractFlowElement(final String id) {
    this.id = BufferUtil.wrapString(id);
    elementType = BpmnElementType.UNSPECIFIED;
  }

  @Override
  public DirectBuffer getId() {
    return id;
  }

  @Override
  public BpmnElementType getElementType() {
    return elementType;
  }

  public void setElementType(final BpmnElementType elementType) {
    this.elementType = elementType;
  }

  @Override
  public ExecutableFlowElement getFlowScope() {
    return flowScope;
  }

  public void setFlowScope(final ExecutableFlowElement flowScope) {
    this.flowScope = flowScope;
  }
}
