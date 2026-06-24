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
public class AgentInstanceCreatedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentInstanceState agentInstanceState;
  private MutableElementInstanceState elementInstanceState;
  private AgentInstanceCreatedApplier applier;

  @BeforeEach
  public void setup() {
    agentInstanceState = processingState.getAgentInstanceState();
    elementInstanceState = processingState.getElementInstanceState();
    applier = new AgentInstanceCreatedApplier(agentInstanceState, elementInstanceState);
  }

  @Test
  void shouldPersistRecordUnderAgentInstanceKey() {
    // given
    final long agentInstanceKey = 42L;
    final long elementInstanceKey = 99L;
    givenElementInstance(elementInstanceKey);
    final var record =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setElementInstanceKeys(List.of(elementInstanceKey))
            .setElementId("agent-task")
            .setProcessInstanceKey(7L)
            .setProcessDefinitionKey(3L)
            .setProcessDefinitionVersion(1)
            .setTenantId("<default>")
            .setStatus(AgentInstanceStatus.INITIALIZING);

    // when
    applier.applyState(agentInstanceKey, record);

    // then
    final var stored = agentInstanceState.getRecord(agentInstanceKey);
    assertThat(stored).isNotNull();
    assertThat(stored.getAgentInstanceKey()).isEqualTo(agentInstanceKey);
    assertThat(stored.getElementInstanceKey()).isEqualTo(elementInstanceKey);
    assertThat(stored.getElementInstanceKeys()).containsExactly(elementInstanceKey);
    assertThat(stored.getElementId()).isEqualTo("agent-task");
    assertThat(stored.getProcessInstanceKey()).isEqualTo(7L);
    assertThat(stored.getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);
  }

  @Test
  void shouldWriteAgentInstanceKeyBackLinkOnParentElementInstance() {
    // given
    final long agentInstanceKey = 42L;
    final long elementInstanceKey = 99L;
    givenElementInstance(elementInstanceKey);
    final var record =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setElementInstanceKey(elementInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING);

    // when
    applier.applyState(agentInstanceKey, record);

    // then
    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    assertThat(elementInstance).isNotNull();
    assertThat(elementInstance.getAgentInstanceKey()).isEqualTo(agentInstanceKey);
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
