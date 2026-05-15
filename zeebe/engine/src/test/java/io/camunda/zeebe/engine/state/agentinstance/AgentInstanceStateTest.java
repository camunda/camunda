/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.engine.state.mutable.MutableAgentInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class AgentInstanceStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableAgentInstanceState agentInstanceState;

  @Before
  public void setUp() {
    final MutableProcessingState processingState = stateRule.getProcessingState();
    agentInstanceState = processingState.getAgentInstanceState();
  }

  @Test
  public void shouldStoreAndLoadRecord() {
    // given
    final long key = 4242L;
    final var record =
        new AgentInstanceRecord()
            .setAgentInstanceKey(key)
            .setElementInstanceKey(1234L)
            .setStatus(AgentInstanceStatus.INITIALIZING);

    // when
    agentInstanceState.insert(key, record);

    // then
    final var loaded = agentInstanceState.getRecord(key);
    assertThat(loaded).isNotNull();
    assertThat(loaded.getAgentInstanceKey()).isEqualTo(key);
    assertThat(loaded.getElementInstanceKey()).isEqualTo(1234L);
    assertThat(loaded.getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);
  }

  @Test
  public void shouldReturnNullForMissingRecord() {
    assertThat(agentInstanceState.getRecord(9999L)).isNull();
  }

  @Test
  public void shouldFailToOverwriteOnSecondStore() {
    // given
    final long key = 4242L;
    agentInstanceState.insert(
        key,
        new AgentInstanceRecord()
            .setAgentInstanceKey(key)
            .setStatus(AgentInstanceStatus.INITIALIZING));

    // when / then
    Assertions.assertThatThrownBy(
            () -> {
              agentInstanceState.insert(
                  key,
                  new AgentInstanceRecord()
                      .setAgentInstanceKey(key)
                      .setStatus(AgentInstanceStatus.THINKING));
            })
        .isInstanceOf(ZeebeDbInconsistentException.class);
  }
}
