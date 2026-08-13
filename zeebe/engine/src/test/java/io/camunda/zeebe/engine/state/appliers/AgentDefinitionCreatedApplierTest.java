/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

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
public class AgentDefinitionCreatedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentDefinitionState agentDefinitionState;
  private AgentDefinitionCreatedApplier applier;

  @BeforeEach
  public void setup() {
    agentDefinitionState = processingState.getAgentDefinitionState();
    applier = new AgentDefinitionCreatedApplier(agentDefinitionState);
  }

  @Test
  void shouldPersistAgentDefinitionKeyByProcessDefinitionKeyAndElementId() {
    // given
    final long agentDefinitionKey = 42L;
    final long processDefinitionKey = 3L;
    final var record =
        new AgentDefinitionRecord()
            .setAgentDefinitionKey(agentDefinitionKey)
            .setAgentType(AgentDefinitionType.AI_AGENT_TASK)
            .setName("agent")
            .setElementId("agent-task")
            .setBpmnProcessId("process")
            .setProcessDefinitionKey(processDefinitionKey)
            .setProcessDefinitionVersion(1)
            .setTenantId("<default>");

    // when
    applier.applyState(agentDefinitionKey, record);

    // then
    final var stored =
        agentDefinitionState.getAgentDefinitionKey(
            processDefinitionKey, BufferUtil.wrapString("agent-task"));
    assertThat(stored).isEqualTo(agentDefinitionKey);
  }

  @Test
  void shouldPersistFullAgentDefinitionRecordByAgentDefinitionKey() {
    // given
    final long agentDefinitionKey = 42L;
    final long processDefinitionKey = 3L;
    final var record =
        new AgentDefinitionRecord()
            .setAgentDefinitionKey(agentDefinitionKey)
            .setAgentType(AgentDefinitionType.AI_AGENT_TASK)
            .setName("agent")
            .setElementId("agent-task")
            .setBpmnProcessId("process")
            .setProcessDefinitionKey(processDefinitionKey)
            .setProcessDefinitionVersion(1)
            .setTenantId("<default>");

    // when
    applier.applyState(agentDefinitionKey, record);

    // then
    final var stored = agentDefinitionState.getAgentDefinition(agentDefinitionKey);
    assertThat(stored)
        .as(
            "Expecting the full record to be reconstructable by its agent definition key, so it"
                + " can be emitted when the owning process definition is deleted")
        .extracting(
            AgentDefinitionRecord::getAgentType,
            AgentDefinitionRecord::getName,
            AgentDefinitionRecord::getElementId,
            AgentDefinitionRecord::getBpmnProcessId,
            AgentDefinitionRecord::getProcessDefinitionKey)
        .containsExactly(
            AgentDefinitionType.AI_AGENT_TASK,
            "agent",
            "agent-task",
            "process",
            processDefinitionKey);
  }
}
