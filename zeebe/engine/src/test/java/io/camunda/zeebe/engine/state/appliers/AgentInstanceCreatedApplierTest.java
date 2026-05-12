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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class AgentInstanceCreatedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentInstanceState agentInstanceState;
  private AgentInstanceCreatedApplier applier;

  @BeforeEach
  public void setup() {
    agentInstanceState = processingState.getAgentInstanceState();
    applier = new AgentInstanceCreatedApplier(agentInstanceState);
  }

  @Test
  void shouldPersistRecordUnderAgentInstanceKey() {
    // given
    final long agentInstanceKey = 42L;
    final var record =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setElementInstanceKey(99L)
            .setElementId("agent-task")
            .setProcessInstanceKey(7L)
            .setProcessDefinitionKey(3L)
            .setProcessDefinitionVersion(1)
            .setTenantId("<default>")
            .setStatus(AgentInstanceStatus.INITIALIZING);

    // when
    applier.applyState(agentInstanceKey, record);

    // then
    assertThat(agentInstanceState.exists(agentInstanceKey)).isTrue();
    final var stored = agentInstanceState.getRecord(agentInstanceKey);
    assertThat(stored).isNotNull();
    assertThat(stored.getAgentInstanceKey()).isEqualTo(agentInstanceKey);
    assertThat(stored.getElementInstanceKey()).isEqualTo(99L);
    assertThat(stored.getElementId()).isEqualTo("agent-task");
    assertThat(stored.getProcessInstanceKey()).isEqualTo(7L);
    assertThat(stored.getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);
  }
}
