/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agentdefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableAgentDefinitionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.agentdefinition.AgentDefinitionRecord;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public final class AgentDefinitionStateTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentDefinitionState agentDefinitionState;

  @BeforeEach
  public void setUp() {
    agentDefinitionState = processingState.getAgentDefinitionState();
  }

  @Test
  void shouldStoreAndLoadAgentDefinitionKey() {
    // given
    final long agentDefinitionKey = 4242L;
    final long processDefinitionKey = 123L;
    final var record =
        new AgentDefinitionRecord()
            .setAgentDefinitionKey(agentDefinitionKey)
            .setAgentType(AgentDefinitionType.AI_AGENT_TASK)
            .setName("agent")
            .setElementId("agentTask")
            .setBpmnProcessId("process")
            .setProcessDefinitionKey(processDefinitionKey)
            .setProcessDefinitionVersion(1)
            .setTenantId("tenant");

    // when
    agentDefinitionState.insert(agentDefinitionKey, record);

    // then
    final var loaded =
        agentDefinitionState.getAgentDefinitionKey(
            processDefinitionKey, BufferUtil.wrapString("agentTask"));
    assertThat(loaded)
        .as("Expecting the inserted agent definition key to be loaded back by its process/element")
        .isEqualTo(agentDefinitionKey);
  }

  @Test
  void shouldReturnNullForMissingAgentDefinitionKey() {
    // given / when
    final var loaded =
        agentDefinitionState.getAgentDefinitionKey(9999L, BufferUtil.wrapString("unknown"));

    // then
    assertThat(loaded)
        .as("Should return null for a process/element pair with no stored agent definition")
        .isNull();
  }

  @Test
  void shouldNotReturnKeyForDifferentProcessDefinitionKey() {
    // given
    final long agentDefinitionKey = 1L;
    final long processDefinitionKey = 100L;
    final long otherProcessDefinitionKey = 200L;
    agentDefinitionState.insert(
        agentDefinitionKey,
        new AgentDefinitionRecord()
            .setAgentDefinitionKey(agentDefinitionKey)
            .setAgentType(AgentDefinitionType.AI_AGENT_TASK)
            .setElementId("agentTask")
            .setProcessDefinitionKey(processDefinitionKey));

    // when
    final var loaded =
        agentDefinitionState.getAgentDefinitionKey(
            otherProcessDefinitionKey, BufferUtil.wrapString("agentTask"));

    // then
    assertThat(loaded)
        .as("Should not return a key stored under a different process definition key")
        .isNull();
  }

  @Test
  void shouldNotReturnKeyForDifferentElementId() {
    // given
    final long agentDefinitionKey = 1L;
    final long processDefinitionKey = 100L;
    agentDefinitionState.insert(
        agentDefinitionKey,
        new AgentDefinitionRecord()
            .setAgentDefinitionKey(agentDefinitionKey)
            .setAgentType(AgentDefinitionType.AI_AGENT_TASK)
            .setElementId("agentTask")
            .setProcessDefinitionKey(processDefinitionKey));

    // when
    final var loaded =
        agentDefinitionState.getAgentDefinitionKey(
            processDefinitionKey, BufferUtil.wrapString("otherElement"));

    // then
    assertThat(loaded).as("Should not return a key stored under a different element id").isNull();
  }
}
