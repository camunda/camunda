/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.agrona.concurrent.UnsafeBuffer;
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
    assertThat(copy.getProcessDefinitionKey()).isEqualTo(original.getProcessDefinitionKey());
    assertThat(copy.getProcessDefinitionVersion())
        .isEqualTo(original.getProcessDefinitionVersion());
    assertThat(copy.getVersionTag()).isEqualTo(original.getVersionTag());
    assertThat(copy.getTenantId()).isEqualTo(original.getTenantId());
  }

  @Test
  void shouldDefaultStatusToInitializing() {
    final AgentInstanceRecord record = new AgentInstanceRecord();
    assertThat(record.getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);
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
}
