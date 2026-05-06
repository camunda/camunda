/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class MessageSubscriptionRecordCopyTest {

  @Test
  void shouldCreateIndependentByteForByteCopyWhenCopyingFromAnotherRecord() {
    // given
    final byte[] messageNameBytes = "message-a".getBytes(StandardCharsets.UTF_8);
    final byte[] correlationKeyBytes = "correlation-a".getBytes(StandardCharsets.UTF_8);
    final byte[] bpmnProcessIdBytes = "process-a".getBytes(StandardCharsets.UTF_8);
    final byte[] originalVariablesBytes = MsgPackConverter.convertToMsgPack(Map.of("a", "x"));

    final byte[] mutatedMessageNameBytes = "message-z".getBytes(StandardCharsets.UTF_8);
    final byte[] mutatedCorrelationKeyBytes = "correlation-z".getBytes(StandardCharsets.UTF_8);
    final byte[] mutatedBpmnProcessIdBytes = "process-z".getBytes(StandardCharsets.UTF_8);
    final byte[] mutatedVariablesBytes = MsgPackConverter.convertToMsgPack(Map.of("b", "y"));

    assertThat(mutatedMessageNameBytes).hasSameSizeAs(messageNameBytes);
    assertThat(mutatedCorrelationKeyBytes).hasSameSizeAs(correlationKeyBytes);
    assertThat(mutatedBpmnProcessIdBytes).hasSameSizeAs(bpmnProcessIdBytes);
    assertThat(mutatedVariablesBytes).hasSameSizeAs(originalVariablesBytes);

    final var source =
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(1L)
            .setElementInstanceKey(2L)
            .setProcessDefinitionKey(3L)
            .setMessageKey(4L)
            .setMessageName(new UnsafeBuffer(messageNameBytes))
            .setCorrelationKey(new UnsafeBuffer(correlationKeyBytes))
            .setInterrupting(false)
            .setBpmnProcessId(new UnsafeBuffer(bpmnProcessIdBytes))
            .setVariables(new UnsafeBuffer(originalVariablesBytes))
            .setTenantId("tenant-a");

    final byte[] expectedSerializedCopy = BufferUtil.bufferAsArray(source);
    final var copy = new MessageSubscriptionRecord();

    // when
    copy.copyFrom(source);

    System.arraycopy(mutatedMessageNameBytes, 0, messageNameBytes, 0, messageNameBytes.length);
    System.arraycopy(
        mutatedCorrelationKeyBytes, 0, correlationKeyBytes, 0, correlationKeyBytes.length);
    System.arraycopy(
        mutatedBpmnProcessIdBytes, 0, bpmnProcessIdBytes, 0, bpmnProcessIdBytes.length);
    System.arraycopy(
        mutatedVariablesBytes, 0, originalVariablesBytes, 0, originalVariablesBytes.length);

    // then
    assertThat(source.getMessageName()).isEqualTo("message-z");
    assertThat(source.getCorrelationKey()).isEqualTo("correlation-z");
    assertThat(source.getBpmnProcessId()).isEqualTo("process-z");
    assertThat(source.getVariables()).isEqualTo(Map.of("b", "y"));

    assertThat(copy.getMessageName()).isEqualTo("message-a");
    assertThat(copy.getCorrelationKey()).isEqualTo("correlation-a");
    assertThat(copy.getBpmnProcessId()).isEqualTo("process-a");
    assertThat(copy.getVariables()).isEqualTo(Map.of("a", "x"));
    assertThat(BufferUtil.bufferAsArray(copy)).containsExactly(expectedSerializedCopy);
  }
}
