/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.AgentInfo;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteCommandRequest;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class CommandApiRequestReaderTest {

  private static UnsafeBuffer encodeCorrelationRecord() {
    final var record = new MessageCorrelationRecord().setName("msg").setCorrelationKey("key");
    final var buf = new UnsafeBuffer(new byte[record.getLength()]);
    record.write(buf, 0);
    return buf;
  }

  @Test
  void shouldDecodeAgentInfoFromRequest() {
    // given
    final var valueBuffer = encodeCorrelationRecord();
    final var agentInfo = new AgentInfo().setToolName("my-tool").setElementId("start-event-1");
    final var request =
        new ExecuteCommandRequest()
            .setValueType(ValueType.MESSAGE_CORRELATION)
            .setIntent(MessageCorrelationIntent.CORRELATE)
            .setValue(valueBuffer, 0, valueBuffer.capacity())
            .setAgentInfo(agentInfo);

    final var buffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(buffer, 0);

    final var reader = new CommandApiRequestReader();

    // when
    reader.wrap(buffer, 0, buffer.capacity());

    // then
    assertThat(reader.metadata().getAgent()).isNotNull();
    assertThat(reader.metadata().getAgent().getToolName()).isEqualTo("my-tool");
    assertThat(reader.metadata().getAgent().getElementId()).isEqualTo("start-event-1");
  }

  @Test
  void shouldDecodeRequestWithoutAgentInfo() {
    // given — no agentInfo set
    final var valueBuffer = encodeCorrelationRecord();
    final var request =
        new ExecuteCommandRequest()
            .setValueType(ValueType.MESSAGE_CORRELATION)
            .setIntent(MessageCorrelationIntent.CORRELATE)
            .setValue(valueBuffer, 0, valueBuffer.capacity());

    final var buffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(buffer, 0);

    final var reader = new CommandApiRequestReader();

    // when
    reader.wrap(buffer, 0, buffer.capacity());

    // then — agent is empty when no agentInfo was encoded
    final var agent = reader.metadata().getAgent();
    assertThat(agent == null || agent.getToolName().isEmpty()).isTrue();
  }

  @Test
  void shouldDecodeAgentToolNameWithNoElementId() {
    // given
    final var valueBuffer = encodeCorrelationRecord();
    final var agentInfo = new AgentInfo().setToolName("correlate-tool");
    final var request =
        new ExecuteCommandRequest()
            .setValueType(ValueType.MESSAGE_CORRELATION)
            .setIntent(MessageCorrelationIntent.CORRELATE)
            .setValue(valueBuffer, 0, valueBuffer.capacity())
            .setAgentInfo(agentInfo);

    final var buffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(buffer, 0);

    final var reader = new CommandApiRequestReader();

    // when
    reader.wrap(buffer, 0, buffer.capacity());

    // then
    assertThat(reader.metadata().getAgent()).isNotNull();
    assertThat(reader.metadata().getAgent().getToolName()).isEqualTo("correlate-tool");
    assertThat(reader.metadata().getAgent().getElementId()).isEmpty();
  }
}
