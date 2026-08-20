/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import org.junit.jupiter.api.Test;

final class BrokerCreateAgentHistoryRequestTest {

  private static final long AGENT_INSTANCE_KEY = 9007199254741017L;

  @Test
  void shouldUseAgentHistoryValueType() {
    final var request = new BrokerCreateAgentHistoryRequest(record(AGENT_INSTANCE_KEY));
    assertThat(request.getValueType()).isEqualTo(ValueType.AGENT_HISTORY);
  }

  @Test
  void shouldUseCreateIntent() {
    final var request = new BrokerCreateAgentHistoryRequest(record(AGENT_INSTANCE_KEY));
    assertThat(request.getIntent()).isEqualTo(AgentHistoryIntent.CREATE);
  }

  @Test
  void shouldRouteToPartitionDecodedFromAgentInstanceKey() {
    final var request = new BrokerCreateAgentHistoryRequest(record(AGENT_INSTANCE_KEY));
    assertThat(request.getPartitionId()).isEqualTo(Protocol.decodePartitionId(AGENT_INSTANCE_KEY));
  }

  @Test
  void shouldReturnEmptyDispatchStrategy() {
    final var request = new BrokerCreateAgentHistoryRequest(record(AGENT_INSTANCE_KEY));
    assertThat(request.requestDispatchStrategy()).isEmpty();
  }

  private static AgentHistoryRecord record(final long agentInstanceKey) {
    final var record = new AgentHistoryRecord();
    record.setAgentInstanceKey(agentInstanceKey);
    return record;
  }
}
