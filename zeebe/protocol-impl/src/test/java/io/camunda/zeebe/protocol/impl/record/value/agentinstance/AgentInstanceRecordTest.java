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
    assertThat(record.getProcessDefinitionVersion()).isEqualTo(-1);
    assertThat(record.getVersionTag()).isEmpty();
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
            .setProcessDefinitionVersion(3)
            .setVersionTag("v1.2")
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
    assertThat(copy.getProcessDefinitionVersion())
        .isEqualTo(original.getProcessDefinitionVersion());
    assertThat(copy.getVersionTag()).isEqualTo(original.getVersionTag());
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
    final AgentInstanceRecord original = new AgentInstanceRecord();
    original
        .getDefinition()
        .setModel("gpt-4o")
        .setProvider("openai")
        .setSystemPrompt("Extract vendor, amount, date.");

    // when
    final AgentInstanceRecord copy = new AgentInstanceRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getDefinition().getModel()).isEqualTo("gpt-4o");
    assertThat(copy.getDefinition().getProvider()).isEqualTo("openai");
    assertThat(copy.getDefinition().getSystemPrompt()).isEqualTo("Extract vendor, amount, date.");
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
        .setModelCalls(3)
        .setToolCalls(2);

    // when
    final AgentInstanceRecord copy = new AgentInstanceRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getMetrics().getInputTokens()).isEqualTo(1340L);
    assertThat(copy.getMetrics().getOutputTokens()).isEqualTo(490L);
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
}
