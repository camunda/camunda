/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agentdefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.engine.state.mutable.MutableAgentDefinitionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.agentdefinition.AgentDefinitionRecord;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
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

  @Test
  void shouldStoreAndLoadFullAgentDefinitionRecord() {
    // given
    final long agentDefinitionKey = 4242L;
    final var record =
        new AgentDefinitionRecord()
            .setAgentDefinitionKey(agentDefinitionKey)
            .setAgentType(AgentDefinitionType.AI_AGENT_TASK)
            .setName("agent")
            .setElementId("agentTask")
            .setBpmnProcessId("process")
            .setProcessDefinitionKey(123L)
            .setProcessDefinitionVersion(1)
            .setProcessDefinitionVersionTag("v1.0")
            .setTenantId("tenant");

    // when
    agentDefinitionState.insert(agentDefinitionKey, record);

    // then
    final var loaded = agentDefinitionState.getAgentDefinition(agentDefinitionKey);
    assertThat(loaded)
        .as("Expecting the full record to be reconstructable by its agent definition key")
        .extracting(
            AgentDefinitionRecord::getAgentDefinitionKey,
            AgentDefinitionRecord::getAgentType,
            AgentDefinitionRecord::getName,
            AgentDefinitionRecord::getElementId,
            AgentDefinitionRecord::getBpmnProcessId,
            AgentDefinitionRecord::getProcessDefinitionKey,
            AgentDefinitionRecord::getProcessDefinitionVersion,
            AgentDefinitionRecord::getProcessDefinitionVersionTag,
            AgentDefinitionRecord::getTenantId)
        .containsExactly(
            agentDefinitionKey,
            AgentDefinitionType.AI_AGENT_TASK,
            "agent",
            "agentTask",
            "process",
            123L,
            1,
            "v1.0",
            "tenant");
  }

  @Test
  void shouldReturnNullForMissingAgentDefinition() {
    // given / when
    final var loaded = agentDefinitionState.getAgentDefinition(9999L);

    // then
    assertThat(loaded).as("Should return null for an unknown agent definition key").isNull();
  }

  @Test
  void shouldEnumerateAgentDefinitionKeysByProcessDefinitionKey() {
    // given
    final long processDefinitionKey = 100L;
    final long otherProcessDefinitionKey = 200L;
    agentDefinitionState.insert(
        1L,
        new AgentDefinitionRecord()
            .setAgentDefinitionKey(1L)
            .setElementId("agentTaskA")
            .setProcessDefinitionKey(processDefinitionKey));
    agentDefinitionState.insert(
        2L,
        new AgentDefinitionRecord()
            .setAgentDefinitionKey(2L)
            .setElementId("agentTaskB")
            .setProcessDefinitionKey(processDefinitionKey));
    agentDefinitionState.insert(
        3L,
        new AgentDefinitionRecord()
            .setAgentDefinitionKey(3L)
            .setElementId("agentTaskC")
            .setProcessDefinitionKey(otherProcessDefinitionKey));

    // when
    final List<Long> collected = new ArrayList<>();
    agentDefinitionState.forEachAgentDefinitionKey(processDefinitionKey, collected::add);

    // then
    assertThat(collected)
        .as("Should enumerate only the agent definition keys of the given process definition")
        .containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  void shouldNotEnumerateAnyKeysForProcessDefinitionWithoutAgentDefinitions() {
    // given / when
    final List<Long> collected = new ArrayList<>();
    agentDefinitionState.forEachAgentDefinitionKey(12345L, collected::add);

    // then
    assertThat(collected)
        .as("Should not enumerate any keys for a process definition with no agent definitions")
        .isEmpty();
  }

  @Test
  void shouldUpsertWithoutThrowingOnReapplication() {
    // given
    final long agentDefinitionKey = 1L;
    final long processDefinitionKey = 100L;
    final var record =
        new AgentDefinitionRecord()
            .setAgentDefinitionKey(agentDefinitionKey)
            .setAgentType(AgentDefinitionType.AI_AGENT_TASK)
            .setElementId("agentTask")
            .setProcessDefinitionKey(processDefinitionKey);

    // when
    agentDefinitionState.insert(agentDefinitionKey, record);

    // then - a redistributed/replayed AgentDefinition:CREATED for the same key does not throw an
    // exception
    assertThatCode(() -> agentDefinitionState.insert(agentDefinitionKey, record))
        .doesNotThrowAnyException();
  }
}
