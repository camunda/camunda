/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Kept separate from {@code AgentInstanceCompleteTest}, which instead drives {@code
 * AgentInstanceIntent.COMPLETE} directly against the processor — this class is about the BPMN
 * lifecycle trigger point, not the command/event round trip itself.
 */
public class AgentInstanceCompleteOnProcessInstanceLifecycleTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String AGENT_TASK_ID = "agent-task";
  private static final String AGENT_JOB_TYPE =
      JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX + "-agent-instance-complete-test";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCompleteAgentInstanceWhenProcessInstanceCompletes() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(AGENT_TASK_ID, t -> t.zeebeJobType(AGENT_JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var agentTaskInstance = awaitElementActivated(processInstanceKey, AGENT_TASK_ID);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(agentTaskInstance.getKey())
            .create()
            .getKey();
    awaitAndActivateJob(AGENT_JOB_TYPE);

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(AGENT_JOB_TYPE).complete();

    // then
    assertThat(
            RecordingExporter.agentInstanceRecords(AgentInstanceIntent.COMPLETED)
                .withRecordKey(agentInstanceKey)
                .exists())
        .describedAs("Agent instance is completed when the process instance completes")
        .isTrue();
  }

  @Test
  public void shouldCompleteAgentInstanceWhenProcessInstanceCanceled() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(AGENT_TASK_ID, t -> t.zeebeJobType(AGENT_JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var agentTaskInstance = awaitElementActivated(processInstanceKey, AGENT_TASK_ID);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(agentTaskInstance.getKey())
            .create()
            .getKey();
    awaitAndActivateJob(AGENT_JOB_TYPE);

    // when — the agentic job is still active (never completed) when cancellation happens
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.agentInstanceRecords(AgentInstanceIntent.COMPLETED)
                .withRecordKey(agentInstanceKey)
                .exists())
        .describedAs(
            "Agent instance is completed even though its agentic job was still active when the "
                + "process instance was canceled")
        .isTrue();
  }

  @Test
  public void
      shouldDiscardPendingAgentHistoryBeforeCompletingAgentInstanceWhenProcessInstanceCanceled() {
    // given
    final String agenticJobType = JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX;
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(AGENT_TASK_ID, t -> t.zeebeJobType(agenticJobType))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var agentTaskInstance = awaitElementActivated(processInstanceKey, AGENT_TASK_ID);
    final long elementInstanceKey = agentTaskInstance.getKey();
    final long agentInstanceKey =
        ENGINE.agentInstances().withElementInstanceKey(elementInstanceKey).create().getKey();

    awaitAndActivateJob(agenticJobType);
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(agenticJobType)
            .getFirst()
            .getKey();

    final long itemKey =
        ENGINE
            .agentHistories()
            .withAgentInstanceKey(agentInstanceKey)
            .withJobKey(jobKey)
            .withElementInstanceKey(elementInstanceKey)
            .withRole(AgentHistoryRole.USER)
            .create()
            .getKey();

    // when — the agentic job and its just-created (not yet committed or discarded) history
    // item are still active when the process instance is canceled
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final var discarded =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.DISCARDED)
            .withRecordKey(itemKey)
            .getFirst();
    final var completed =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.COMPLETED)
            .withRecordKey(agentInstanceKey)
            .getFirst();
    assertThat(discarded.getPosition())
        .describedAs(
            "Pending AGENT_HISTORY item is discarded, as part of the agentic job's own "
                + "cancellation, strictly before the agent instance that job belongs to is "
                + "completed")
        .isLessThan(completed.getPosition());
  }

  @Test
  public void shouldCompleteAllAgentInstancesOfProcessInstance() {
    // given — two parallel agentic service tasks, each with its own agent instance
    final String otherAgentTaskId = "other-agent-task";
    final String otherAgentJobType = "other-agent";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask(AGENT_TASK_ID, t -> t.zeebeJobType(AGENT_JOB_TYPE))
                .parallelGateway("join")
                .endEvent()
                .moveToNode("fork")
                .serviceTask(otherAgentTaskId, t -> t.zeebeJobType(otherAgentJobType))
                .connectTo("join")
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var firstTaskInstance = awaitElementActivated(processInstanceKey, AGENT_TASK_ID);
    final var secondTaskInstance = awaitElementActivated(processInstanceKey, otherAgentTaskId);
    final var firstAgentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(firstTaskInstance.getKey())
            .create()
            .getKey();
    final var secondAgentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(secondTaskInstance.getKey())
            .create()
            .getKey();
    awaitAndActivateJob(AGENT_JOB_TYPE);
    awaitAndActivateJob(otherAgentJobType);

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(AGENT_JOB_TYPE).complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(otherAgentJobType).complete();

    // then
    assertThat(
            RecordingExporter.agentInstanceRecords(AgentInstanceIntent.COMPLETED)
                .limit(2)
                .map(Record::getKey))
        .describedAs("Both agent instances belonging to the process instance are completed")
        .containsExactlyInAnyOrder(firstAgentInstanceKey, secondAgentInstanceKey);
  }

  @Test
  public void shouldCompleteChildProcessInstanceAgentInstancesIndependentlyOfParent() {
    // given — the parent runs the call activity in parallel with its own long-running task, so
    // the parent process instance is still active once the child (and its agent instance)
    // completes
    final String parentProcessId = "parent-process";
    final String childProcessId = "child-process";
    final String callActivityId = "call-child";
    final String parentTaskId = "parent-task";
    final String parentJobType = "parent-job";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask(AGENT_TASK_ID, t -> t.zeebeJobType(AGENT_JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .parallelGateway("fork")
                .callActivity(callActivityId, ca -> ca.zeebeProcessId(childProcessId))
                .parallelGateway("join")
                .endEvent()
                .moveToNode("fork")
                .serviceTask(parentTaskId, t -> t.zeebeJobType(parentJobType))
                .connectTo("join")
                .done())
        .deploy();

    final var parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    final var childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withBpmnProcessId(childProcessId)
            .getFirst();
    final long childProcessInstanceKey = childProcessInstance.getValue().getProcessInstanceKey();

    final var agentTaskInstance = awaitElementActivated(childProcessInstanceKey, AGENT_TASK_ID);
    final long agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(agentTaskInstance.getKey())
            .create()
            .getKey();

    awaitAndActivateJob(AGENT_JOB_TYPE);

    // when — the child process instance completes (completing its agent instance), while the
    // parent's own parallel branch (parentTaskId) is still active
    ENGINE.job().ofInstance(childProcessInstanceKey).withType(AGENT_JOB_TYPE).complete();

    // then
    assertThat(
            RecordingExporter.agentInstanceRecords(AgentInstanceIntent.COMPLETED)
                .withRecordKey(agentInstanceKey)
                .exists())
        .describedAs("Child process instance's agent instance is completed")
        .isTrue();
    final long clockResetKey = ENGINE.clock().reset().getKey();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.PROCESS_INSTANCE)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .filter(
                    r -> {
                      final var value = (ProcessInstanceRecordValue) r.getValue();
                      return value.getProcessInstanceKey() == parentProcessInstanceKey
                          && value.getBpmnElementType() == BpmnElementType.PROCESS;
                    })
                .exists())
        .describedAs(
            "Parent process instance has not completed yet, since its own parallel branch is "
                + "still active")
        .isFalse();

    // when — completing the remaining parallel branch lets the parent process instance complete
    // too
    awaitAndActivateJob(parentJobType);
    ENGINE.job().ofInstance(parentProcessInstanceKey).withType(parentJobType).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(parentProcessInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs(
            "Parent process instance completes once its remaining parallel branch finishes, "
                + "confirming the two lifecycles were genuinely independent rather than "
                + "coincidental")
        .isTrue();
  }

  @Test
  public void shouldCompleteChildProcessInstanceAgentInstancesWhenParentIsCanceled() {
    // given — a call activity with no other parallel branch, so canceling the parent is the only
    // way the child (and its still-active agentic job) ever terminates
    final String parentProcessId = "parent-process";
    final String childProcessId = "child-process";
    final String callActivityId = "call-child";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask(AGENT_TASK_ID, t -> t.zeebeJobType(AGENT_JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity(callActivityId, ca -> ca.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .deploy();

    final var parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    final var childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withBpmnProcessId(childProcessId)
            .getFirst();
    final long childProcessInstanceKey = childProcessInstance.getValue().getProcessInstanceKey();

    final var agentTaskInstance = awaitElementActivated(childProcessInstanceKey, AGENT_TASK_ID);
    final long agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(agentTaskInstance.getKey())
            .create()
            .getKey();

    awaitAndActivateJob(AGENT_JOB_TYPE);

    // when — the agentic job on the child process instance is still active when the parent
    // (and, cascading from it, the child) is canceled
    ENGINE.processInstance().withInstanceKey(parentProcessInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.agentInstanceRecords(AgentInstanceIntent.COMPLETED)
                .withRecordKey(agentInstanceKey)
                .exists())
        .describedAs(
            "Child process instance's agent instance is completed when the parent process "
                + "instance is canceled, cascading termination down through the call activity")
        .isTrue();
  }

  private static Record<ProcessInstanceRecordValue> awaitElementActivated(
      final long processInstanceKey, final String elementId) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withElementId(elementId)
        .getFirst();
  }

  private static void awaitAndActivateJob(final String jobType) {
    RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).await();
    ENGINE.jobs().withType(jobType).activate();
  }
}
