/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** */
public class AgentInfo extends UnpackedObject implements Agent {

  private final StringProperty elementIdProp = new StringProperty("elementId", "");

  public AgentInfo() {
    super(1);
    declareProperty(elementIdProp);
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public AgentInfo setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  @Override
  public void reset() {
    elementIdProp.setValue("");
  }

  @Override
  @JsonIgnore
  public int getEncodedLength() {
    return super.getEncodedLength();
  }

  @Override
  @JsonIgnore
  public boolean isEmpty() {
    return super.isEmpty();
  }

  @Override
  @JsonIgnore
  public int getLength() {
    return super.getLength();
  }

  public DirectBuffer toDirectBuffer() {
    final var bytes = new byte[getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    write(buffer, 0);

    return buffer;
  }

  public static AgentInfo of(final Agent agent) {
    if (agent != null) {
      return new AgentInfo().setElementId(agent.getElementId());
    } else {
      return null;
    }
  }
}
