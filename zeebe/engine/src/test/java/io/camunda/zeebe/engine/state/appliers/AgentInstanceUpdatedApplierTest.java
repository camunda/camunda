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
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.List;
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
    updatedApplier = new AgentInstanceUpdatedApplier(agentInstanceState, elementInstanceState);
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
    // given — a CREATED record, EI₁ and EI₂ seeded, and an UPDATED record applied once.
    final long agentInstanceKey = 11L;
    final long ei1Key = 21L;
    final long ei2Key = 22L;
    givenElementInstance(ei1Key);
    givenElementInstance(ei2Key);

    final var initial =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setElementInstanceKey(ei1Key)
            .setElementInstanceKeys(List.of(ei1Key))
            .setStatus(AgentInstanceStatus.INITIALIZING);
    createdApplier.applyState(agentInstanceKey, initial);

    // Apply an UPDATED event that appends EI₂.
    final var updated =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setElementInstanceKey(ei2Key)
            .setElementInstanceKeys(List.of(ei1Key, ei2Key))
            .setStatus(AgentInstanceStatus.THINKING);
    updated.getMetrics().setInputTokens(42L);

    updatedApplier.applyState(agentInstanceKey, updated);
    final var afterFirst = agentInstanceState.getRecord(agentInstanceKey);
    final var ei2AfterFirst = elementInstanceState.getInstance(ei2Key);

    // when — replay the same UPDATED event.
    updatedApplier.applyState(agentInstanceKey, updated);

    // then — the stored state is identical after the replay.
    final var afterReplay = agentInstanceState.getRecord(agentInstanceKey);
    assertThat(afterReplay).isNotNull();
    assertThat(afterReplay.getStatus()).isEqualTo(afterFirst.getStatus());
    assertThat(afterReplay.getMetrics().getInputTokens())
        .isEqualTo(afterFirst.getMetrics().getInputTokens());
    assertThat(afterReplay.getStatus()).isEqualTo(AgentInstanceStatus.THINKING);
    assertThat(afterReplay.getMetrics().getInputTokens()).isEqualTo(42L);
    // The plural list still carries [EI₁, EI₂].
    assertThat(afterReplay.getElementInstanceKeys()).containsExactly(ei1Key, ei2Key);
    // EI₂'s back-link remains == agent instance key.
    final var ei2AfterReplay = elementInstanceState.getInstance(ei2Key);
    assertThat(ei2AfterReplay).isNotNull();
    assertThat(ei2AfterReplay.getAgentInstanceKey()).isEqualTo(agentInstanceKey);
    assertThat(ei2AfterReplay.getAgentInstanceKey()).isEqualTo(ei2AfterFirst.getAgentInstanceKey());
  }

  @Test
  void shouldSetAgentInstanceKeyBackLinkOnNewlyAssociatedElementInstance() {
    // given — EI₁ with an existing back-link, EI₂ with no back-link.
    final long agentInstanceKey = 42L;
    final long ei1Key = 101L;
    final long ei2Key = 102L;
    givenElementInstance(ei1Key);
    givenElementInstance(ei2Key);

    final var initial =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setElementInstanceKey(ei1Key)
            .setElementInstanceKeys(List.of(ei1Key))
            .setStatus(AgentInstanceStatus.INITIALIZING);
    createdApplier.applyState(agentInstanceKey, initial);

    // Verify EI₁ has a back-link, EI₂ does not.
    assertThat(elementInstanceState.getInstance(ei1Key).getAgentInstanceKey())
        .isEqualTo(agentInstanceKey);
    assertThat(elementInstanceState.getInstance(ei2Key).getAgentInstanceKey()).isEqualTo(-1L);

    // when — apply an UPDATED event that associates EI₂.
    final var updated =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setElementInstanceKey(ei2Key)
            .setElementInstanceKeys(List.of(ei1Key, ei2Key))
            .setStatus(AgentInstanceStatus.THINKING);
    updatedApplier.applyState(agentInstanceKey, updated);

    // then — EI₂ now has the back-link set.
    final var ei2 = elementInstanceState.getInstance(ei2Key);
    assertThat(ei2).isNotNull();
    assertThat(ei2.getAgentInstanceKey()).isEqualTo(agentInstanceKey);
  }

  @Test
  void shouldAppendElementInstanceKeyToPluralList() {
    // given — an agent instance associated with EI₁.
    final long agentInstanceKey = 55L;
    final long ei1Key = 201L;
    final long ei2Key = 202L;
    givenElementInstance(ei1Key);
    givenElementInstance(ei2Key);

    final var initial =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setElementInstanceKey(ei1Key)
            .setElementInstanceKeys(List.of(ei1Key))
            .setStatus(AgentInstanceStatus.INITIALIZING);
    createdApplier.applyState(agentInstanceKey, initial);

    // when — apply an UPDATED event appending EI₂.
    final var updated =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setElementInstanceKey(ei2Key)
            .setElementInstanceKeys(List.of(ei1Key, ei2Key))
            .setStatus(AgentInstanceStatus.THINKING);
    updatedApplier.applyState(agentInstanceKey, updated);

    // then — the persisted record carries [EI₁, EI₂] in the plural list.
    final var stored = agentInstanceState.getRecord(agentInstanceKey);
    assertThat(stored).isNotNull();
    assertThat(stored.getElementInstanceKeys()).containsExactly(ei1Key, ei2Key);
    assertThat(stored.getElementInstanceKey()).isEqualTo(ei2Key);
  }

  private void givenElementInstance(final long elementInstanceKey) {
    final var processInstanceRecord =
        new ProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.SERVICE_TASK)
            .setElementId("agent-task")
            .setBpmnProcessId("process")
            .setProcessDefinitionKey(3L)
            .setProcessInstanceKey(7L)
            .setVersion(1)
            .setTenantId("<default>");
    elementInstanceState.newInstance(
        elementInstanceKey, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }
}
