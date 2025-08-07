/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collections;
import java.util.Map;
import org.agrona.DirectBuffer;

public abstract class AbstractFlowElement implements ExecutableFlowElement {

  private final DirectBuffer id;
  private DirectBuffer name;
  private DirectBuffer documentation;
  private BpmnElementType elementType;
  private BpmnEventType eventType;
  private ExecutableFlowElement flowScope;
  private Map<String, String> properties = Collections.emptyMap();

  public AbstractFlowElement(final String id) {
    this.id = BufferUtil.wrapString(id);
    elementType = BpmnElementType.UNSPECIFIED;
    eventType = BpmnEventType.UNSPECIFIED;
  }

  @Override
  public DirectBuffer getId() {
    return id;
  }

  @Override
  public DirectBuffer getName() {
    return name;
  }

  public void setName(final DirectBuffer name) {
    this.name = name;
  }

  @Override
  public DirectBuffer getDocumentation() {
    return documentation;
  }

  public void setDocumentation(final DirectBuffer documentation) {
    this.documentation = documentation;
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

  @Override
  public BpmnEventType getEventType() {
    return eventType;
  }

  public void setEventType(final BpmnEventType eventType) {
    this.eventType = eventType;
  }

  @Override
  public Map<String, String> getProperties() {
    return properties;
  }

  public void setProperties(final Map<String, String> properties) {
    this.properties = properties;
  }
}
