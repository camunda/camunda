/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AgentHistoryRecordTest {

  @Test
  void shouldExposeDefaults() {
    // given
    final AgentHistoryRecord record = new AgentHistoryRecord();

    // then
    assertThat(record.getAgentHistoryKey()).isEqualTo(-1L);
    assertThat(record.getAgentInstanceKey()).isEqualTo(-1L);
    assertThat(record.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(record.getJobKey()).isEqualTo(-1L);
    assertThat(record.getJobLease()).isEmpty();
    assertThat(record.getIteration()).isEqualTo(0);
    assertThat(record.getRole()).isEqualTo(AgentHistoryRole.UNSPECIFIED);
    assertThat(record.getProducedAt()).isEqualTo(-1L);
    assertThat(record.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(record.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(-1L);
    assertThat(record.getBpmnProcessId()).isEmpty();
    assertThat(record.getProcessDefinitionKey()).isEqualTo(-1L);
  }

  @Test
  void shouldRoundTripScalarFieldsViaMsgPack() {
    // given
    final AgentHistoryRecord original =
        new AgentHistoryRecord()
            .setAgentHistoryKey(2251799813685250L)
            .setAgentInstanceKey(2251799813685251L)
            .setElementInstanceKey(2251799813685249L)
            .setJobKey(2251799813685252L)
            .setJobLease("job-lease-abc123")
            .setIteration(3)
            .setRole(AgentHistoryRole.USER)
            .setProducedAt(1717200000000L);

    // when
    final AgentHistoryRecord copy = new AgentHistoryRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getAgentHistoryKey()).isEqualTo(original.getAgentHistoryKey());
    assertThat(copy.getAgentInstanceKey()).isEqualTo(original.getAgentInstanceKey());
    assertThat(copy.getElementInstanceKey()).isEqualTo(original.getElementInstanceKey());
    assertThat(copy.getJobKey()).isEqualTo(original.getJobKey());
    assertThat(copy.getJobLease()).isEqualTo(original.getJobLease());
    assertThat(copy.getIteration()).isEqualTo(original.getIteration());
    assertThat(copy.getRole()).isEqualTo(original.getRole());
    assertThat(copy.getProducedAt()).isEqualTo(original.getProducedAt());
  }

  @Test
  void shouldRoundTripJobLeaseViaMsgPack() {
    // given
    final AgentHistoryRecord original = new AgentHistoryRecord().setJobLease("job-lease-abc123");

    // when
    final AgentHistoryRecord copy = new AgentHistoryRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getJobLease()).isEqualTo("job-lease-abc123");
  }

  @Test
  void shouldRoundTripRoleViaMsgPack() {
    // given
    final AgentHistoryRecord original =
        new AgentHistoryRecord().setRole(AgentHistoryRole.ASSISTANT);

    // when
    final AgentHistoryRecord copy = new AgentHistoryRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getRole()).isEqualTo(AgentHistoryRole.ASSISTANT);
  }

  @Test
  void shouldExposeDefaultContent() {
    // given
    final AgentHistoryRecord record = new AgentHistoryRecord();

    // then
    assertThat(record.getContent()).isEmpty();
  }

  @Test
  void shouldRoundTripContentViaMsgPack() {
    // given
    final var textBlock =
        new AgentHistoryMessageContent()
            .setContentType(AgentHistoryContentType.TEXT)
            .setText("Hello, world!");

    final var documentBlock =
        new AgentHistoryMessageContent().setContentType(AgentHistoryContentType.DOCUMENT);
    documentBlock
        .getDocumentReference()
        .setDocumentId("doc-123")
        .setStoreId("store-456")
        .setContentHash("sha256-doc123");

    final Map<String, Object> objectData = Map.of("key", "value");
    final var objectBlock =
        new AgentHistoryMessageContent()
            .setContentType(AgentHistoryContentType.OBJECT)
            .setObject(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(objectData)));

    final AgentHistoryRecord original =
        new AgentHistoryRecord().setContent(List.of(textBlock, documentBlock, objectBlock));

    // when
    final AgentHistoryRecord copy = new AgentHistoryRecord();
    copy.copyFrom(original);

    // then
    final var content = copy.getContent();
    assertThat(content).hasSize(3);

    final var text = content.get(0);
    assertThat(text.getContentType()).isEqualTo(AgentHistoryContentType.TEXT);
    assertThat(text.getText()).isEqualTo("Hello, world!");

    final var document = content.get(1);
    assertThat(document.getContentType()).isEqualTo(AgentHistoryContentType.DOCUMENT);
    assertThat(document.getDocumentReference().getDocumentId()).isEqualTo("doc-123");
    assertThat(document.getDocumentReference().getStoreId()).isEqualTo("store-456");
    assertThat(document.getDocumentReference().getContentHash()).isEqualTo("sha256-doc123");

    final var object = content.get(2);
    assertThat(object.getContentType()).isEqualTo(AgentHistoryContentType.OBJECT);
    assertThat(object.getObject()).isEqualTo(objectData);
  }

  @Test
  void shouldExposeDefaultToolCalls() {
    // given
    final AgentHistoryRecord record = new AgentHistoryRecord();

    // then
    assertThat(record.getToolCalls()).isEmpty();
  }

  @Test
  void shouldExposeDefaultMetrics() {
    // given
    final AgentHistoryRecord record = new AgentHistoryRecord();

    // then
    assertThat(record.getMetrics().getInputTokens()).isEqualTo(0L);
    assertThat(record.getMetrics().getOutputTokens()).isEqualTo(0L);
    assertThat(record.getMetrics().getDurationMs()).isEqualTo(0L);
  }

  @Test
  void shouldRoundTripToolCallsViaMsgPack() {
    // given
    final Map<String, Object> args = Map.of("param", "value");
    final var toolCall =
        new AgentHistoryEmbeddedToolCall()
            .setToolCallId("call-123")
            .setToolName("myTool")
            .setElementId("element-456")
            .setArguments(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(args)));

    final AgentHistoryRecord original = new AgentHistoryRecord().setToolCalls(List.of(toolCall));

    // when
    final AgentHistoryRecord copy = new AgentHistoryRecord();
    copy.copyFrom(original);

    // then
    final var toolCalls = copy.getToolCalls();
    assertThat(toolCalls).hasSize(1);

    final var copied = toolCalls.get(0);
    assertThat(copied.getToolCallId()).isEqualTo("call-123");
    assertThat(copied.getToolName()).isEqualTo("myTool");
    assertThat(copied.getElementId()).isEqualTo("element-456");
    assertThat(copied.getArguments()).isEqualTo(args);
  }

  @Test
  void shouldRoundTripMetricsViaMsgPack() {
    // given
    final AgentHistoryRecord original = new AgentHistoryRecord();
    original.getMetrics().setInputTokens(100L).setOutputTokens(200L).setDurationMs(350L);

    // when
    final AgentHistoryRecord copy = new AgentHistoryRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(copy.getMetrics().getOutputTokens()).isEqualTo(200L);
    assertThat(copy.getMetrics().getDurationMs()).isEqualTo(350L);
  }

  @Test
  void shouldRoundTripTenantAndProcessFieldsViaMsgPack() {
    // given
    final AgentHistoryRecord original =
        new AgentHistoryRecord()
            .setBpmnProcessId("my-process")
            .setTenantId("tenant-a")
            .setProcessInstanceKey(2251799813685260L)
            .setRootProcessInstanceKey(2251799813685262L)
            .setProcessDefinitionKey(2251799813685261L);

    // when
    final AgentHistoryRecord copy = new AgentHistoryRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getTenantId()).isEqualTo("tenant-a");
    assertThat(copy.getBpmnProcessId()).isEqualTo("my-process");
    assertThat(copy.getProcessInstanceKey()).isEqualTo(2251799813685260L);
    assertThat(copy.getRootProcessInstanceKey()).isEqualTo(2251799813685262L);
    assertThat(copy.getProcessDefinitionKey()).isEqualTo(2251799813685261L);
  }
}
