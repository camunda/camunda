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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** */
public class AgentInfo extends UnpackedObject implements Agent {

  private final LongProperty idProp = new LongProperty("id", -1L);
  private final StringProperty nameProp = new StringProperty("name", "");

  public AgentInfo() {
    super(2);
    declareProperty(idProp).declareProperty(nameProp);
  }

  @Override
  public long getId() {
    return idProp.getValue();
  }

  public AgentInfo setId(final long agentId) {
    idProp.setValue(agentId);
    return this;
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public AgentInfo setName(final String agentName) {
    nameProp.setValue(agentName);
    return this;
  }

  @Override
  public void reset() {
    idProp.reset();
    nameProp.setValue("");
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
    return new AgentInfo().setId(agent.getId()).setName(agent.getName());
  }
}
