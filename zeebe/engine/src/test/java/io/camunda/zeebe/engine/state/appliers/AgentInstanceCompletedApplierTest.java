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
public class AgentInstanceCompletedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentInstanceState agentInstanceState;
  private AgentInstanceCreatedApplier createdApplier;
  private AgentInstanceCompletedApplier completedApplier;

  @BeforeEach
  public void setup() {
    agentInstanceState = processingState.getAgentInstanceState();
    final MutableElementInstanceState elementInstanceState =
        processingState.getElementInstanceState();
    createdApplier = new AgentInstanceCreatedApplier(agentInstanceState, elementInstanceState);
    completedApplier = new AgentInstanceCompletedApplier(agentInstanceState);
  }

  @Test
  void shouldDeleteRecordAndRemoveFromProcessInstanceKeyIndex() {
    // given — a previously CREATED record associated with a process instance.
    final long agentInstanceKey = 9L;
    final long processInstanceKey = 5L;
    createdApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING));

    // when — apply the COMPLETED event.
    completedApplier.applyState(
        agentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.COMPLETED));

    // then — the record is gone from primary state, and the process-instance-key secondary
    // index no longer returns the deleted key.
    assertThat(agentInstanceState.getRecord(agentInstanceKey)).isNull();
    assertThat(agentInstanceState.getAgentInstanceKeysByProcessInstanceKey(processInstanceKey))
        .isEmpty();
  }

  @Test
  void shouldNotAffectOtherAgentInstancesOfSameProcessInstance() {
    // given — two agent instances associated with the same process instance.
    final long processInstanceKey = 5L;
    final long firstAgentInstanceKey = 11L;
    final long secondAgentInstanceKey = 12L;
    createdApplier.applyState(
        firstAgentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(firstAgentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING));
    createdApplier.applyState(
        secondAgentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(secondAgentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING));

    // when — only the first agent instance is completed.
    completedApplier.applyState(
        firstAgentInstanceKey,
        new AgentInstanceRecord()
            .setAgentInstanceKey(firstAgentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStatus(AgentInstanceStatus.COMPLETED));

    // then — the second agent instance is untouched, in both primary state and the index.
    assertThat(agentInstanceState.getRecord(secondAgentInstanceKey)).isNotNull();
    assertThat(agentInstanceState.getAgentInstanceKeysByProcessInstanceKey(processInstanceKey))
        .containsExactly(secondAgentInstanceKey);
  }
}
