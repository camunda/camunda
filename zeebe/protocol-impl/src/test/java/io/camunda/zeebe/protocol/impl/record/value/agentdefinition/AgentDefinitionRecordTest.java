/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agentdefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.jupiter.api.Test;

final class AgentDefinitionRecordTest {

  @Test
  void shouldExposeIdentityDefaults() {
    // given
    final AgentDefinitionRecord record = new AgentDefinitionRecord();

    // then
    assertThat(record.getAgentDefinitionKey()).isEqualTo(-1L);
    assertThat(record.getName()).isEmpty();
    assertThat(record.getElementId()).isEmpty();
    assertThat(record.getBpmnProcessId()).isEmpty();
    assertThat(record.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(record.getProcessDefinitionVersion()).isEqualTo(-1);
    assertThat(record.getProcessDefinitionVersionTag()).isEmpty();
    assertThat(record.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  void shouldRoundTripIdentityFieldsViaMsgPack() {
    // given
    final AgentDefinitionRecord original =
        new AgentDefinitionRecord()
            .setAgentDefinitionKey(2251799813685251L)
            .setName("Invoice Data Extraction")
            .setElementId("invoice-data-extraction-agent")
            .setBpmnProcessId("invoice-handling-process")
            .setProcessDefinitionKey(2251799813685100L)
            .setProcessDefinitionVersion(3)
            .setProcessDefinitionVersionTag("v1.0")
            .setTenantId("acme");

    // when
    final AgentDefinitionRecord copy = new AgentDefinitionRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getAgentDefinitionKey()).isEqualTo(original.getAgentDefinitionKey());
    assertThat(copy.getName()).isEqualTo(original.getName());
    assertThat(copy.getElementId()).isEqualTo(original.getElementId());
    assertThat(copy.getBpmnProcessId()).isEqualTo(original.getBpmnProcessId());
    assertThat(copy.getProcessDefinitionKey()).isEqualTo(original.getProcessDefinitionKey());
    assertThat(copy.getProcessDefinitionVersion())
        .isEqualTo(original.getProcessDefinitionVersion());
    assertThat(copy.getProcessDefinitionVersionTag())
        .isEqualTo(original.getProcessDefinitionVersionTag());
    assertThat(copy.getTenantId()).isEqualTo(original.getTenantId());
  }

  @Test
  void shouldDefaultAgentTypeToUnspecified() {
    // given
    final AgentDefinitionRecord record = new AgentDefinitionRecord();

    // then
    assertThat(record.getAgentType()).isEqualTo(AgentDefinitionType.UNSPECIFIED);
  }

  @Test
  void shouldRoundTripAgentTypeViaMsgPack() {
    // given
    final AgentDefinitionRecord original =
        new AgentDefinitionRecord().setAgentType(AgentDefinitionType.AI_AGENT_SUB_PROCESS);

    // when
    final AgentDefinitionRecord copy = new AgentDefinitionRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getAgentType()).isEqualTo(AgentDefinitionType.AI_AGENT_SUB_PROCESS);
  }
}
