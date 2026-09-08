/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryEmbeddedToolCall;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceToolValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AgentInstanceRecordTest {

  @Test
  void shouldExposeIdentityDefaults() {
    // given
    final AgentInstanceRecord record = new AgentInstanceRecord();

    // then
    assertThat(record.getAgentInstanceKey()).isEqualTo(-1L);
    assertThat(record.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(record.getElementId()).isEmpty();
    assertThat(record.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(record.getBpmnProcessId()).isEmpty();
    assertThat(record.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(-1L);
    assertThat(record.getProcessDefinitionVersion()).isEqualTo(-1);
    assertThat(record.getAgentDefinitionKey()).isEqualTo(-1L);
    assertThat(record.getProcessDefinitionVersionTag()).isEmpty();
    assertThat(record.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  void shouldRoundTripIdentityFieldsViaMsgPack() {
    // given
    final AgentInstanceRecord original =
        new AgentInstanceRecord()
            .setAgentInstanceKey(2251799813685251L)
            .setElementInstanceKey(2251799813685249L)
            .setElementId("invoice-data-extraction-agent")
            .setProcessInstanceKey(2251799813685248L)
            .setBpmnProcessId("invoice-handling-process")
            .setProcessDefinitionKey(2251799813685100L)
            .setRootProcessInstanceKey(2251799813685000L)
            .setProcessDefinitionVersion(3)
            .setAgentDefinitionKey(2251799813685077L)
            .setProcessDefinitionVersionTag("v1.2")
            .setTenantId("acme");

    // when
    final AgentInstanceRecord copy = new AgentInstanceRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getAgentInstanceKey()).isEqualTo(original.getAgentInstanceKey());
    assertThat(copy.getElementInstanceKey()).isEqualTo(original.getElementInstanceKey());
    assertThat(copy.getElementId()).isEqualTo(original.getElementId());
    assertThat(copy.getProcessInstanceKey()).isEqualTo(original.getProcessInstanceKey());
    assertThat(copy.getBpmnProcessId()).isEqualTo(original.getBpmnProcessId());
    assertThat(copy.getProcessDefinitionKey()).isEqualTo(original.getProcessDefinitionKey());
    assertThat(copy.getRootProcessInstanceKey()).isEqualTo(original.getRootProcessInstanceKey());
    assertThat(copy.getProcessDefinitionVersion())
        .isEqualTo(original.getProcessDefinitionVersion());
    assertThat(copy.getAgentDefinitionKey()).isEqualTo(original.getAgentDefinitionKey());
    assertThat(copy.getProcessDefinitionVersionTag())
        .isEqualTo(original.getProcessDefinitionVersionTag());
    assertThat(copy.getTenantId()).isEqualTo(original.getTenantId());
  }

  @Test
  void shouldDefaultStatusToUnspecified() {
    final AgentInstanceRecord record = new AgentInstanceRecord();
    assertThat(record.getStatus()).isEqualTo(AgentInstanceStatus.UNSPECIFIED);
  }

  @Test
  void shouldRoundTripStatusViaMsgPack() {
    // given
    final AgentInstanceRecord original =
        new AgentInstanceRecord().setStatus(AgentInstanceStatus.THINKING);

    // when
    final AgentInstanceRecord copy = new AgentInstanceRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getStatus()).isEqualTo(AgentInstanceStatus.THINKING);
  }

  @Test
  void shouldDefaultDefinitionFieldsToEmpty() {
    final AgentInstanceRecord record = new AgentInstanceRecord();
    assertThat(record.getDefinition().getModel()).isEmpty();
    assertThat(record.getDefinition().getProvider()).isEmpty();
    assertThat(record.getDefinition().getSystemPrompt()).isEmpty();
  }

  @Test
  void shouldRoundTripDefinitionViaMsgPack() {
    // given
    final var systemPromptBlock =
        new AgentHistoryMessageContent()
            .setContentType(AgentHistoryContentType.TEXT)
            .setText("Extract vendor, amount, date.");
    final AgentInstanceRecord original = new AgentInstanceRecord();
    original
        .getDefinition()
        .setModel("gpt-4o")
        .setProvider("openai")
        .setSystemPrompt(List.of(systemPromptBlock));

    // when
    final AgentInstanceRecord copy = new AgentInstanceRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getDefinition().getModel()).isEqualTo("gpt-4o");
    assertThat(copy.getDefinition().getProvider()).isEqualTo("openai");
    assertThat(copy.getDefinition().getSystemPrompt())
        .extracting(AgentHistoryRecordValue.AgentHistoryMessageContentValue::getText)
        .containsExactly("Extract vendor, amount, date.");
  }

  @Test
  void shouldDefaultLimitsToMinusOne() {
    final AgentInstanceRecord record = new AgentInstanceRecord();
    assertThat(record.getLimits().getMaxTokens()).isEqualTo(-1L);
    assertThat(record.getLimits().getMaxModelCalls()).isEqualTo(-1);
    assertThat(record.getLimits().getMaxToolCalls()).isEqualTo(-1);
  }

  @Test
  void shouldRoundTripLimitsViaMsgPack() {
    // given
    final AgentInstanceRecord original = new AgentInstanceRecord();
    original.getLimits().setMaxTokens(8000L).setMaxModelCalls(10).setMaxToolCalls(20);

    // when
    final AgentInstanceRecord copy = new AgentInstanceRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getLimits().getMaxTokens()).isEqualTo(8000L);
    assertThat(copy.getLimits().getMaxModelCalls()).isEqualTo(10);
    assertThat(copy.getLimits().getMaxToolCalls()).isEqualTo(20);
  }

  @Test
  void shouldDefaultMetricsToZero() {
    final AgentInstanceRecord record = new AgentInstanceRecord();
    assertThat(record.getMetrics().getInputTokens()).isZero();
    assertThat(record.getMetrics().getOutputTokens()).isZero();
    assertThat(record.getMetrics().getReasoningTokenCount()).isZero();
    assertThat(record.getMetrics().getCacheCreationTokenCount()).isZero();
    assertThat(record.getMetrics().getCacheReadTokenCount()).isZero();
    assertThat(record.getMetrics().getModelCalls()).isZero();
    assertThat(record.getMetrics().getToolCalls()).isZero();
  }

  @Test
  void shouldRoundTripMetricsViaMsgPack() {
    // given
    final AgentInstanceRecord original = new AgentInstanceRecord();
    original
        .getMetrics()
        .setInputTokens(1340L)
        .setOutputTokens(490L)
        .setReasoningTokenCount(120L)
        .setCacheCreationTokenCount(80L)
        .setCacheReadTokenCount(40L)
        .setModelCalls(3)
        .setToolCalls(2);

    // when
    final AgentInstanceRecord copy = new AgentInstanceRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getMetrics().getInputTokens()).isEqualTo(1340L);
    assertThat(copy.getMetrics().getOutputTokens()).isEqualTo(490L);
    assertThat(copy.getMetrics().getReasoningTokenCount()).isEqualTo(120L);
    assertThat(copy.getMetrics().getCacheCreationTokenCount()).isEqualTo(80L);
    assertThat(copy.getMetrics().getCacheReadTokenCount()).isEqualTo(40L);
    assertThat(copy.getMetrics().getModelCalls()).isEqualTo(3);
    assertThat(copy.getMetrics().getToolCalls()).isEqualTo(2);
  }

  @Test
  void shouldDefaultToolsToEmptyList() {
    final AgentInstanceRecord record = new AgentInstanceRecord();
    assertThat(record.getTools()).isEmpty();
  }

  @Test
  void shouldRoundTripToolsViaMsgPack() {
    // given
    final AgentInstanceTool first =
        new AgentInstanceTool()
            .setName("extract_line_items")
            .setElementId("extract-line-items-task");
    final AgentInstanceTool second =
        new AgentInstanceTool()
            .setName("MCP_ocr___scan_document")
            .setDescription("OCR a PDF")
            .setElementId("MCP_ocr");
    final AgentInstanceRecord original = new AgentInstanceRecord().setTools(List.of(first, second));

    // when
    final AgentInstanceRecord copy = new AgentInstanceRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getTools())
        .extracting(
            AgentInstanceToolValue::getName,
            AgentInstanceToolValue::getDescription,
            AgentInstanceToolValue::getElementId)
        .containsExactly(
            tuple("extract_line_items", "", "extract-line-items-task"),
            tuple("MCP_ocr___scan_document", "OCR a PDF", "MCP_ocr"));
  }

  @Test
  void shouldDefaultElementInstanceKeysToEmptyList() {
    final AgentInstanceRecord record = new AgentInstanceRecord();
    assertThat(record.getElementInstanceKeys()).isEmpty();
  }

  @Test
  void shouldRoundTripElementInstanceKeysViaMsgPack() {
    // given
    final AgentInstanceRecord original =
        new AgentInstanceRecord()
            .setElementInstanceKeys(List.of(2251799813685248L, 2251799813685249L));

    // when
    final AgentInstanceRecord copy = new AgentInstanceRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getElementInstanceKeys()).containsExactly(2251799813685248L, 2251799813685249L);
  }

  @Test
  void shouldReplaceExistingElementInstanceKeysOnSet() {
    // given
    final AgentInstanceRecord record =
        new AgentInstanceRecord().setElementInstanceKeys(List.of(2251799813685248L));

    // when
    record.setElementInstanceKeys(List.of(2251799813685249L, 2251799813685250L));

    // then
    assertThat(record.getElementInstanceKeys())
        .containsExactly(2251799813685249L, 2251799813685250L);
  }

  @Test
  void shouldAppendElementInstanceKey() {
    // given
    final AgentInstanceRecord record =
        new AgentInstanceRecord().setElementInstanceKeys(List.of(1L, 2L));

    // when
    record.addElementInstanceKey(3L);

    // then
    assertThat(record.getElementInstanceKeys()).containsExactly(1L, 2L, 3L);
  }

  @Test
  void shouldDefaultChangedAttributesToEmptyList() {
    final AgentInstanceRecord record = new AgentInstanceRecord();
    assertThat(record.getChangedAttributes()).isEmpty();
  }

  @Test
  void shouldRoundTripChangedAttributesViaMsgPack() {
    // given
    final AgentInstanceRecord original =
        new AgentInstanceRecord().setChangedAttributes(List.of("status", "metrics"));

    // when
    final AgentInstanceRecord copy = new AgentInstanceRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getChangedAttributes()).containsExactly("status", "metrics");
  }

  @Test
  void shouldReplaceExistingChangedAttributesOnSet() {
    // given
    final AgentInstanceRecord record =
        new AgentInstanceRecord().setChangedAttributes(List.of("status"));

    // when
    record.setChangedAttributes(List.of("metrics", "tools"));

    // then
    assertThat(record.getChangedAttributes()).containsExactly("metrics", "tools");
  }

  @Test
  void shouldReplaceExistingToolsOnSet() {
    // given
    final AgentInstanceRecord record =
        new AgentInstanceRecord().setTools(List.of(new AgentInstanceTool().setName("first")));

    // when
    record.setTools(List.of(new AgentInstanceTool().setName("second")));

    // then
    assertThat(record.getTools())
        .extracting(AgentInstanceToolValue::getName)
        .containsExactly("second");
  }

  @Test
  void shouldDefaultJobFieldsToUnset() {
    final AgentInstanceRecord record = new AgentInstanceRecord();
    assertThat(record.getJobKey()).isEqualTo(-1L);
    assertThat(record.getJobLease()).isEmpty();
  }

  @Test
  void shouldRoundTripJobFieldsViaMsgPack() {
    // given
    final AgentInstanceRecord original =
        new AgentInstanceRecord().setJobKey(2251799813685300L).setJobLease("job-lease-xyz789");

    // when
    final AgentInstanceRecord copy = new AgentInstanceRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getJobKey()).isEqualTo(2251799813685300L);
    assertThat(copy.getJobLease()).isEqualTo("job-lease-xyz789");
  }

  @Test
  void shouldDefaultHistoryToEmptyList() {
    final AgentInstanceRecord record = new AgentInstanceRecord();
    assertThat(record.getHistory()).isEmpty();
  }

  @Test
  void shouldRoundTripHistoryViaMsgPack() {
    // given
    final var userItem =
        new AgentHistoryRecord().setRole(AgentHistoryRole.USER).setProducedAt(1717200000000L);
    userItem.addContent(
        new AgentHistoryMessageContent()
            .setContentType(AgentHistoryContentType.TEXT)
            .setText("Please extract the invoice data."));

    final var assistantItem =
        new AgentHistoryRecord().setRole(AgentHistoryRole.ASSISTANT).setProducedAt(1717199999000L);
    assistantItem.addContent(
        new AgentHistoryMessageContent()
            .setContentType(AgentHistoryContentType.TEXT)
            .setText("Here is the extracted invoice data."));
    assistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-1").setToolName("noop"));
    assistantItem.getMetrics().setInputTokens(10L).setOutputTokens(20L).setDurationMs(30L);

    final AgentInstanceRecord original =
        new AgentInstanceRecord().setHistory(List.of(assistantItem, userItem));

    // when
    final AgentInstanceRecord copy = new AgentInstanceRecord();
    copy.copyFrom(original);

    // then
    final var history = copy.getHistory();
    assertThat(history).hasSize(2);

    final AgentHistoryRecordValue firstItem = history.get(0);
    assertThat(firstItem.getRole()).isEqualTo(AgentHistoryRole.ASSISTANT);
    assertThat(firstItem.getContent()).hasSize(1);
    assertThat(firstItem.getContent().get(0).getText())
        .isEqualTo("Here is the extracted invoice data.");
    assertThat(firstItem.getToolCalls()).hasSize(1);
    assertThat(firstItem.getToolCalls().get(0).getToolCallId()).isEqualTo("call-1");
    assertThat(firstItem.getMetrics().getInputTokens()).isEqualTo(10L);
    assertThat(firstItem.getMetrics().getOutputTokens()).isEqualTo(20L);
    assertThat(firstItem.getMetrics().getDurationMs()).isEqualTo(30L);
    assertThat(firstItem.getProducedAt()).isEqualTo(1717199999000L);

    final AgentHistoryRecordValue secondItem = history.get(1);
    assertThat(secondItem.getRole()).isEqualTo(AgentHistoryRole.USER);
    assertThat(secondItem.getContent()).hasSize(1);
    assertThat(secondItem.getContent().get(0).getText())
        .isEqualTo("Please extract the invoice data.");
    assertThat(secondItem.getProducedAt()).isEqualTo(1717200000000L);
  }

  @Test
  void shouldReplaceExistingHistoryOnSet() {
    // given
    final AgentInstanceRecord record =
        new AgentInstanceRecord().setHistory(List.of(new AgentHistoryRecord().setProducedAt(1L)));

    // when
    record.setHistory(List.of(new AgentHistoryRecord().setProducedAt(2L)));

    // then
    assertThat(record.getHistory())
        .extracting(AgentHistoryRecordValue::getProducedAt)
        .containsExactly(2L);
  }

  @Test
  void shouldAppendHistoryItem() {
    // given
    final AgentInstanceRecord record =
        new AgentInstanceRecord().setHistory(List.of(new AgentHistoryRecord().setProducedAt(1L)));

    // when
    record.addHistoryItem(new AgentHistoryRecord().setProducedAt(2L));

    // then
    assertThat(record.getHistory())
        .extracting(AgentHistoryRecordValue::getProducedAt)
        .containsExactly(1L, 2L);
  }
}
