/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentinstance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceToolValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

@JsonIgnoreProperties({"encodedLength", "empty"})
public final class AgentInstanceTool extends ObjectValue implements AgentInstanceToolValue {

  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty descriptionProp = new StringProperty("description", "");
  private final StringProperty elementIdProp = new StringProperty("elementId", "");

  public AgentInstanceTool() {
    super(3);
    declareProperty(nameProp).declareProperty(descriptionProp).declareProperty(elementIdProp);
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public AgentInstanceTool setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public String getDescription() {
    return BufferUtil.bufferAsString(descriptionProp.getValue());
  }

  public AgentInstanceTool setDescription(final String description) {
    descriptionProp.setValue(description);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public AgentInstanceTool setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public void copy(final AgentInstanceToolValue other) {
    setName(other.getName());
    setDescription(other.getDescription());
    setElementId(other.getElementId());
  }
}
