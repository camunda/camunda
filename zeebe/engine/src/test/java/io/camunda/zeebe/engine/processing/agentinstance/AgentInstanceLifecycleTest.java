/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceStatus;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class AgentInstanceLifecycleTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldEmitCreatedEventForCreateCommand() {
    // when
    final var record =
        ENGINE_RULE
            .agentInstances()
            .withProcessInstanceKey(123L)
            .withProcessDefinitionKey(456L)
            .withElementInstanceKey(789L)
            .withElementId("agent-task")
            .withModel("claude-opus-4-7")
            .withProvider("anthropic")
            .create();

    // then
    Assertions.assertThat(record)
        .hasIntent(AgentInstanceIntent.CREATED)
        .hasRecordType(RecordType.EVENT);
  }

  @Test
  public void shouldEmitUpdatedEventForUpdateCommand() {
    // given
    final var created =
        ENGINE_RULE
            .agentInstances()
            .withProcessInstanceKey(124L)
            .withProcessDefinitionKey(456L)
            .withElementInstanceKey(790L)
            .withElementId("agent-task")
            .withModel("claude-opus-4-7")
            .withProvider("anthropic")
            .create();
    final long agentInstanceKey = created.getValue().getAgentInstanceKey();

    // when
    final var record =
        ENGINE_RULE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withStatus(AgentInstanceStatus.THINKING)
            .withInputTokens(100L)
            .withOutputTokens(200L)
            .withModelCalls(1L)
            .update();

    // then
    Assertions.assertThat(record)
        .hasIntent(AgentInstanceIntent.UPDATED)
        .hasRecordType(RecordType.EVENT);
  }

  @Test
  public void shouldEmitDeletedEventForDeleteCommand() {
    // given
    final var created =
        ENGINE_RULE
            .agentInstances()
            .withProcessInstanceKey(125L)
            .withProcessDefinitionKey(456L)
            .withElementInstanceKey(791L)
            .withElementId("agent-task")
            .withModel("claude-opus-4-7")
            .withProvider("anthropic")
            .create();
    final long agentInstanceKey = created.getValue().getAgentInstanceKey();

    // when
    final var record = ENGINE_RULE.agentInstances().withAgentInstanceKey(agentInstanceKey).delete();

    // then
    Assertions.assertThat(record)
        .hasIntent(AgentInstanceIntent.DELETED)
        .hasRecordType(RecordType.EVENT);
  }
}
