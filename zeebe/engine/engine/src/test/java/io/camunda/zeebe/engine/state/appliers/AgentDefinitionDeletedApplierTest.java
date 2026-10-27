/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
public class AgentDefinitionDeletedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentDefinitionState agentDefinitionState;
  private AgentDefinitionCreatedApplier createdApplier;
  private AgentDefinitionDeletedApplier deletedApplier;

  @BeforeEach
  public void setup() {
    agentDefinitionState = processingState.getAgentDefinitionState();
    createdApplier = new AgentDefinitionCreatedApplier(agentDefinitionState);
    deletedApplier = new AgentDefinitionDeletedApplier(agentDefinitionState);
  }

  @Test
  void shouldRemoveAgentDefinitionKeyByProcessDefinitionKeyAndElementId() {
    // given
    final var record = sampleAgentDefinitionRecord();
    createdApplier.applyState(record.getAgentDefinitionKey(), record);

    // when
    deletedApplier.applyState(record.getAgentDefinitionKey(), record);

    // then
    final var stored =
        agentDefinitionState.getAgentDefinitionKey(
            record.getProcessDefinitionKey(), BufferUtil.wrapString(record.getElementId()));
    assertThat(stored)
        .as("Expecting the (processDefinitionKey, elementId) -> agentDefinitionKey entry removed")
        .isNull();
  }

  @Test
  void shouldRemoveFullAgentDefinitionRecordByAgentDefinitionKey() {
    // given
    final var record = sampleAgentDefinitionRecord();
    createdApplier.applyState(record.getAgentDefinitionKey(), record);

    // when
    deletedApplier.applyState(record.getAgentDefinitionKey(), record);

    // then
    final var stored = agentDefinitionState.getAgentDefinition(record.getAgentDefinitionKey());
    assertThat(stored)
        .as("Expecting the agentDefinitionKey -> DbAgentDefinition entry removed")
        .isNull();
  }

  @Test
  void shouldBeIdempotentWhenAgentDefinitionWasNeverCreated() {
    // given
    final var record = sampleAgentDefinitionRecord();

    // when / then - a redistributed/replayed AgentDefinition:DELETED must not throw, even if the
    // entries are already gone (e.g. on distribution retry)
    assertThatCode(() -> deletedApplier.applyState(record.getAgentDefinitionKey(), record))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldBeIdempotentWhenAppliedTwice() {
    // given
    final var record = sampleAgentDefinitionRecord();
    createdApplier.applyState(record.getAgentDefinitionKey(), record);
    deletedApplier.applyState(record.getAgentDefinitionKey(), record);

    // when / then - a redistributed/replayed AgentDefinition:DELETED for an already-deleted key
    // must not throw
    assertThatCode(() -> deletedApplier.applyState(record.getAgentDefinitionKey(), record))
        .doesNotThrowAnyException();
  }

  private static AgentDefinitionRecord sampleAgentDefinitionRecord() {
    return new AgentDefinitionRecord()
        .setAgentDefinitionKey(42L)
        .setAgentType(AgentDefinitionType.AI_AGENT_TASK)
        .setName("agent")
        .setElementId("agent-task")
        .setBpmnProcessId("process")
        .setProcessDefinitionKey(3L)
        .setProcessDefinitionVersion(1)
        .setTenantId("<default>");
  }
}
