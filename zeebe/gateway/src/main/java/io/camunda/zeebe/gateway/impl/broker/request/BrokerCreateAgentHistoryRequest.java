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
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import org.agrona.DirectBuffer;

public class BrokerCreateAgentHistoryRequest extends BrokerExecuteCommand<AgentHistoryRecord> {

  private final AgentHistoryRecord requestDto;

  public BrokerCreateAgentHistoryRequest(final AgentHistoryRecord record) {
    super(ValueType.AGENT_HISTORY, AgentHistoryIntent.CREATE);
    requestDto = record;
    // Route to the partition that owns the agent instance, decoded from the key.
    request.setPartitionId(Protocol.decodePartitionId(record.getAgentInstanceKey()));
  }

  @Override
  public AgentHistoryRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected AgentHistoryRecord toResponseDto(final DirectBuffer buffer) {
    final AgentHistoryRecord responseDto = new AgentHistoryRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
