/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import org.agrona.DirectBuffer;

public class BrokerUpdateAgentInstanceRequest extends BrokerExecuteCommand<AgentInstanceRecord> {

  private final AgentInstanceRecord requestDto;

  public BrokerUpdateAgentInstanceRequest(final AgentInstanceRecord record) {
    super(ValueType.AGENT_INSTANCE, AgentInstanceIntent.UPDATE);
    requestDto = record;
    request.setKey(record.getAgentInstanceKey());
    // Route to the partition that owns the agent instance, decoded from the key.
    // All keys in Zeebe encode the owning partition in their high bits.
    request.setPartitionId(Protocol.decodePartitionId(record.getAgentInstanceKey()));
  }

  @Override
  public AgentInstanceRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected AgentInstanceRecord toResponseDto(final DirectBuffer buffer) {
    final AgentInstanceRecord responseDto = new AgentInstanceRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
