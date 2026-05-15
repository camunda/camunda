/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import static io.camunda.zeebe.engine.util.client.AgentInstanceClient.tool;
import static io.camunda.zeebe.engine.util.client.AgentInstanceClient.tools;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AgentInstanceUpdateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "service-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  /**
   * Deploys a process, creates a process instance, awaits the service-task activation, sends a
   * CREATE agent instance command and returns the agentInstanceKey from the resulting CREATED
   * event.
   */
  private long createAgentInstance() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var serviceTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst();

    return ENGINE
        .agentInstances()
        .withElementInstanceKey(serviceTaskInstance.getKey())
        .create()
        .getValue()
        .getAgentInstanceKey();
  }

  @Test
  public void shouldEmitUpdatedEventForValidUpdateCommand() {
    // given
    final var agentInstanceKey = createAgentInstance();

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withStatus(AgentInstanceStatus.THINKING)
            .update();

    // then
    assertThat(updated.getKey()).isEqualTo(agentInstanceKey);
    assertThat(updated.getValue().getStatus()).isEqualTo(AgentInstanceStatus.THINKING);
    assertThat(updated.getValue().getChangedAttributes()).containsExactly("status");
  }

  @Test
  public void shouldReplaceToolsListEntirely() {
    // given
    final var agentInstanceKey = createAgentInstance();
    final var firstTools = tools(tool("t1", "first tool", "t1-elem"));
    final var secondTools = tools(tool("t2", "second tool", "t2-elem"));

    // when
    ENGINE.agentInstances().withAgentInstanceKey(agentInstanceKey).withTools(firstTools).update();
    final var second =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withTools(secondTools)
            .update();

    // then — final tools list contains only the second tool; the first is gone.
    assertThat(second.getValue().getTools())
        .singleElement()
        .satisfies(t -> assertThat(t.getName()).isEqualTo("t2"));
  }

  @Test
  public void shouldClearToolsListWhenEmptyListProvided() {
    // given
    final var agentInstanceKey = createAgentInstance();

    // when — first add a tool, then clear with an empty list.
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withTools(tools(tool("t1", "first tool", "t1-elem")))
        .update();
    final var cleared =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withTools(List.of())
            .update();

    // then
    assertThat(cleared.getValue().getTools()).isEmpty();
  }

  @Test
  public void shouldAllowStatusTransitionsBetweenActiveStates() {
    // given — verify the matrix of active-state -> active-state transitions, including same-state.
    final var activeStates =
        List.of(
            AgentInstanceStatus.INITIALIZING,
            AgentInstanceStatus.TOOL_DISCOVERY,
            AgentInstanceStatus.THINKING,
            AgentInstanceStatus.TOOL_CALLING,
            AgentInstanceStatus.IDLE);

    for (final var from : activeStates) {
      for (final var to : activeStates) {
        // Skip transitions to INITIALIZING from non-INITIALIZING states — those are rejected.
        if (to == AgentInstanceStatus.INITIALIZING && from != AgentInstanceStatus.INITIALIZING) {
          continue;
        }
        final var agentInstanceKey = createAgentInstance();
        // Move the agent into the "from" state if necessary.
        if (from != AgentInstanceStatus.INITIALIZING) {
          ENGINE.agentInstances().withAgentInstanceKey(agentInstanceKey).withStatus(from).update();
        }
        // when
        final var updated =
            ENGINE.agentInstances().withAgentInstanceKey(agentInstanceKey).withStatus(to).update();

        // then
        assertThat(updated.getIntent())
            .as("expected transition from %s to %s to be allowed", from, to)
            .isEqualTo(AgentInstanceIntent.UPDATED);
        assertThat(updated.getValue().getStatus()).isEqualTo(to);
      }
    }
  }
}
