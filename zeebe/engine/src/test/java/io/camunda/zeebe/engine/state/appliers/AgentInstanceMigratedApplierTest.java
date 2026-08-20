/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableAgentInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class AgentInstanceMigratedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentInstanceState agentInstanceState;
  private AgentInstanceCreatedApplier createdApplier;
  private AgentInstanceMigratedApplier migratedApplier;

  @BeforeEach
  public void setup() {
    agentInstanceState = processingState.getAgentInstanceState();
    final MutableElementInstanceState elementInstanceState =
        processingState.getElementInstanceState();
    createdApplier = new AgentInstanceCreatedApplier(agentInstanceState, elementInstanceState);
    migratedApplier = new AgentInstanceMigratedApplier(agentInstanceState);
  }

  @Test
  void shouldUpdateProcessDefinitionFields() {
    // given — an agent instance created against the source process definition.
    final long agentInstanceKey = 7L;
    createdApplier.applyState(agentInstanceKey, sourceRecord(agentInstanceKey));

    // when — a MIGRATED event re-points it at the target process definition.
    migratedApplier.applyState(agentInstanceKey, targetRecord(agentInstanceKey));

    // then — the stored record reflects the target process definition and remapped element id.
    final var stored = agentInstanceState.getRecord(agentInstanceKey);
    assertThat(stored).isNotNull();
    assertThat(stored.getProcessDefinitionKey()).isEqualTo(200L);
    assertThat(stored.getProcessDefinitionVersion()).isEqualTo(2);
    assertThat(stored.getBpmnProcessId()).isEqualTo("target-process");
    assertThat(stored.getVersionTag()).isEqualTo("v2.0");
    assertThat(stored.getElementId()).isEqualTo("agent2");
  }

  @Test
  void shouldKeepAgentInstanceRetrievableByProcessInstanceKey() {
    // given — an agent instance created against the source process definition.
    final long agentInstanceKey = 11L;
    createdApplier.applyState(agentInstanceKey, sourceRecord(agentInstanceKey));

    // when — a MIGRATED event is applied (the process instance key is unchanged by migration).
    migratedApplier.applyState(agentInstanceKey, targetRecord(agentInstanceKey));

    // then — the process-instance secondary index still resolves the agent instance.
    assertThat(agentInstanceState.getAgentInstanceKeysByProcessInstanceKey(3L))
        .containsExactly(agentInstanceKey);
  }

  private AgentInstanceRecord sourceRecord(final long agentInstanceKey) {
    return new AgentInstanceRecord()
        .setAgentInstanceKey(agentInstanceKey)
        .setProcessInstanceKey(3L)
        .setProcessDefinitionKey(100L)
        .setProcessDefinitionVersion(1)
        .setBpmnProcessId("source-process")
        .setVersionTag("v1.0")
        .setElementId("agent")
        .setStatus(AgentInstanceStatus.INITIALIZING);
  }

  private AgentInstanceRecord targetRecord(final long agentInstanceKey) {
    return new AgentInstanceRecord()
        .setAgentInstanceKey(agentInstanceKey)
        .setProcessInstanceKey(3L)
        .setProcessDefinitionKey(200L)
        .setProcessDefinitionVersion(2)
        .setBpmnProcessId("target-process")
        .setVersionTag("v2.0")
        .setElementId("agent2")
        .setStatus(AgentInstanceStatus.INITIALIZING);
  }
}
