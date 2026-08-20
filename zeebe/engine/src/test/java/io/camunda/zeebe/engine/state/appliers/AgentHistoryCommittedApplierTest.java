/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableAgentHistoryState;
import io.camunda.zeebe.engine.state.mutable.MutableAgentInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class AgentHistoryCommittedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableAgentInstanceState agentInstanceState;
  private MutableAgentHistoryState agentHistoryState;
  private AgentHistoryCommittedApplier committedApplier;

  @BeforeEach
  public void setup() {
    agentInstanceState = processingState.getAgentInstanceState();
    agentHistoryState = processingState.getAgentHistoryState();
    committedApplier = new AgentHistoryCommittedApplier(processingState);
  }

  @Test
  void shouldSyncCommittedSnapshotWhenConfigurationItemCommits() {
    // given — an agent instance whose live definition/tools/limits already reflect the
    // optimistically-applied change (as done by AgentHistoryBatchBehavior at CREATE time).
    final long agentInstanceKey = 1L;
    final var live = new AgentInstanceRecord().setAgentInstanceKey(agentInstanceKey);
    live.getLimits().setMaxTokens(111).setMaxModelCalls(2).setMaxToolCalls(3);
    live.getDefinition()
        .setModel("gpt-5")
        .setProvider("openai")
        .setSystemPrompt(
            List.of(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("be helpful")));
    agentInstanceState.insert(agentInstanceKey, live);

    final long historyKey = 10L;
    final var item =
        new AgentHistoryRecord()
            .setAgentHistoryKey(historyKey)
            .setAgentInstanceKey(agentInstanceKey)
            .setRole(AgentHistoryRole.CONFIGURATION);
    agentHistoryState.insert(historyKey, item);

    // when
    committedApplier.applyState(historyKey, item);

    // then
    final var snapshot = agentInstanceState.getCommittedSnapshot(agentInstanceKey);
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.getDefinition().getModel()).isEqualTo("gpt-5");
    assertThat(snapshot.getDefinition().getProvider()).isEqualTo("openai");
    assertThat(snapshot.getDefinition().getSystemPrompt()).hasSize(1);
    assertThat(snapshot.getDefinition().getSystemPrompt().get(0).getText()).isEqualTo("be helpful");
    assertThat(snapshot.getLimits().getMaxTokens()).isEqualTo(111);
    assertThat(snapshot.getLimits().getMaxModelCalls()).isEqualTo(2);
    assertThat(snapshot.getLimits().getMaxToolCalls()).isEqualTo(3);
  }

  @Test
  void shouldNotSyncSnapshotForNonConfigurationItem() {
    // given
    final long agentInstanceKey = 1L;
    agentInstanceState.insert(
        agentInstanceKey, new AgentInstanceRecord().setAgentInstanceKey(agentInstanceKey));

    final long historyKey = 10L;
    final var item =
        new AgentHistoryRecord()
            .setAgentHistoryKey(historyKey)
            .setAgentInstanceKey(agentInstanceKey)
            .setRole(AgentHistoryRole.ASSISTANT);
    agentHistoryState.insert(historyKey, item);

    // when
    committedApplier.applyState(historyKey, item);

    // then
    assertThat(agentInstanceState.getCommittedSnapshot(agentInstanceKey)).isNull();
  }

  @Test
  void shouldNotFailWhenAgentInstanceIsAlreadyGone() {
    // given — a CONFIGURATION item whose agent instance was already deleted (e.g. the process
    // instance completed before this COMMIT was processed).
    final long agentInstanceKey = 1L;
    final long historyKey = 10L;
    final var item =
        new AgentHistoryRecord()
            .setAgentHistoryKey(historyKey)
            .setAgentInstanceKey(agentInstanceKey)
            .setRole(AgentHistoryRole.CONFIGURATION);
    agentHistoryState.insert(historyKey, item);

    // when — applying should not throw despite there being no live agent instance to read from
    committedApplier.applyState(historyKey, item);

    // then — no snapshot is created, and the history item is still deleted as usual
    assertThat(agentInstanceState.getCommittedSnapshot(agentInstanceKey)).isNull();
    assertThat(agentHistoryState.get(historyKey)).isNull();
  }

  @Test
  void shouldStillDeleteHistoryItemOnCommit() {
    // given
    final long agentInstanceKey = 1L;
    agentInstanceState.insert(
        agentInstanceKey, new AgentInstanceRecord().setAgentInstanceKey(agentInstanceKey));

    final long historyKey = 10L;
    final var item =
        new AgentHistoryRecord()
            .setAgentHistoryKey(historyKey)
            .setAgentInstanceKey(agentInstanceKey)
            .setRole(AgentHistoryRole.CONFIGURATION);
    agentHistoryState.insert(historyKey, item);

    // when
    committedApplier.applyState(historyKey, item);

    // then — the existing delete-from-primary-storage behavior is unaffected by the new sync.
    assertThat(agentHistoryState.get(historyKey)).isNull();
  }
}
