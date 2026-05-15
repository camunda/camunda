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
public class AgentInstanceUpdatedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentInstanceState agentInstanceState;
  private MutableElementInstanceState elementInstanceState;
  private AgentInstanceCreatedApplier createdApplier;
  private AgentInstanceUpdatedApplier updatedApplier;

  @BeforeEach
  public void setup() {
    agentInstanceState = processingState.getAgentInstanceState();
    elementInstanceState = processingState.getElementInstanceState();
    createdApplier = new AgentInstanceCreatedApplier(agentInstanceState, elementInstanceState);
    updatedApplier = new AgentInstanceUpdatedApplier(agentInstanceState);
  }

  @Test
  void shouldOverwriteExistingRecord() {
    // given — a previously CREATED record under the key.
    final long agentInstanceKey = 7L;
    final var initial =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING);
    initial.getMetrics().setInputTokens(0L);
    createdApplier.applyState(agentInstanceKey, initial);

    // when — apply an UPDATED event with a new status and metrics snapshot.
    final var updated =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setStatus(AgentInstanceStatus.THINKING);
    updated.getMetrics().setInputTokens(10L).setOutputTokens(5L);
    updatedApplier.applyState(agentInstanceKey, updated);

    // then — the stored record reflects the updated values.
    final var stored = agentInstanceState.getRecord(agentInstanceKey);
    assertThat(stored).isNotNull();
    assertThat(stored.getStatus()).isEqualTo(AgentInstanceStatus.THINKING);
    assertThat(stored.getMetrics().getInputTokens()).isEqualTo(10L);
    assertThat(stored.getMetrics().getOutputTokens()).isEqualTo(5L);
  }

  @Test
  void shouldBeIdempotentWhenReplayed() {
    // given — a CREATED record and an UPDATED record stored once.
    final long agentInstanceKey = 11L;
    final var initial =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING);
    createdApplier.applyState(agentInstanceKey, initial);

    final var updated =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setStatus(AgentInstanceStatus.THINKING);
    updated.getMetrics().setInputTokens(42L);

    updatedApplier.applyState(agentInstanceKey, updated);
    final var afterFirst = agentInstanceState.getRecord(agentInstanceKey);

    // when — replay the same UPDATED event.
    updatedApplier.applyState(agentInstanceKey, updated);

    // then — the stored state is identical (same status & metrics) after the replay.
    final var afterReplay = agentInstanceState.getRecord(agentInstanceKey);
    assertThat(afterReplay).isNotNull();
    assertThat(afterReplay.getStatus()).isEqualTo(afterFirst.getStatus());
    assertThat(afterReplay.getMetrics().getInputTokens())
        .isEqualTo(afterFirst.getMetrics().getInputTokens());
    assertThat(afterReplay.getStatus()).isEqualTo(AgentInstanceStatus.THINKING);
    assertThat(afterReplay.getMetrics().getInputTokens()).isEqualTo(42L);
  }
}
