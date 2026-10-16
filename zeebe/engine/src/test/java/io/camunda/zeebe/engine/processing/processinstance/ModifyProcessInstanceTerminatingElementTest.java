/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Verifies that a modification terminates elements that are already terminating. An element stays
 * in this state when a canceling task listener job of its user task has no retries left.
 */
public class ModifyProcessInstanceTerminatingElementTest {

  private static final String LISTENER_TYPE = "canceling_listener";

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCancelUserTaskWhenTerminatingUserTaskElementThatIsCanceling() {
    // given
    final long processInstanceKey = createProcessInstanceBlockedByCancelingListener();

    // when
    terminateElementByModification(processInstanceKey, "userTask");

    // then
    assertThatCancelingUserTaskIsRemoved(processInstanceKey);
    assertThatIncidentIsResolved(processInstanceKey);
  }

  @Test
  public void shouldTerminateCancelingUserTaskWhenTerminatingFlowScope() {
    // given
    final long processInstanceKey = createProcessInstanceBlockedByCancelingListener();

    // when
    terminateElementByModification(processInstanceKey, "sub");

    // then
    assertThatElementIsTerminated(processInstanceKey, "userTask");
    assertThatCancelingUserTaskIsRemoved(processInstanceKey);
    assertThatIncidentIsResolved(processInstanceKey);
  }

  @Test
  public void shouldTerminateCancelingUserTaskWithRetriedListenerJobWhenTerminatingFlowScope() {
    // given
    final long processInstanceKey = createProcessInstanceBlockedByCancelingListener();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(LISTENER_TYPE)
        .withRetries(1)
        .updateRetries();
    ENGINE
        .incident()
        .ofInstance(processInstanceKey)
        .withKey(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getKey())
        .resolve();

    // when
    terminateElementByModification(processInstanceKey, "sub");

    // then
    assertThatElementIsTerminated(processInstanceKey, "userTask");
    assertThatCancelingUserTaskIsRemoved(processInstanceKey);
  }

  @Test
  public void shouldTerminateCancelingUserTaskInCalledProcessWhenTerminatingCallActivity() {
    // given
    final String childProcessId = Strings.newRandomValidBpmnId();
    final String parentProcessId = Strings.newRandomValidBpmnId();
    ENGINE.deployment().withXmlResource(processWithCancelingListener(childProcessId)).deploy();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("callActivity", c -> c.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .deploy();
    final long parentKey = ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    final long childKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentKey)
            .withBpmnProcessId(childProcessId)
            .getFirst()
            .getKey();
    getUserTaskKey(childKey);
    ENGINE.processInstance().withInstanceKey(parentKey).expectTerminating().cancel();
    failCancelingListenerJob(childKey);

    // when
    terminateElementByModification(parentKey, "callActivity");

    // then
    assertThatElementIsTerminated(childKey, childProcessId);
    assertThatCancelingUserTaskIsRemoved(childKey);
    assertThatIncidentIsResolved(childKey);
  }

  /**
   * Creates a process instance that is canceled while its subprocess contains a user task with a
   * canceling task listener. The listener job fails without retries, so the instance stays
   * terminating and an incident is raised.
   */
  private long createProcessInstanceBlockedByCancelingListener() {
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE.deployment().withXmlResource(processWithCancelingListener(processId)).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    // cancel only once the user task exists, otherwise its canceling listeners would not run
    getUserTaskKey(processInstanceKey);

    ENGINE.processInstance().withInstanceKey(processInstanceKey).expectTerminating().cancel();
    failCancelingListenerJob(processInstanceKey);

    return processInstanceKey;
  }

  private static BpmnModelInstance processWithCancelingListener(final String processId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .subProcess("sub")
        .embeddedSubProcess()
        .startEvent()
        .userTask(
            "userTask",
            u -> u.zeebeUserTask().zeebeTaskListener(l -> l.canceling().type(LISTENER_TYPE)))
        .endEvent()
        .subProcessDone()
        .endEvent()
        .done();
  }

  private static void failCancelingListenerJob(final long processInstanceKey) {
    ENGINE.job().ofInstance(processInstanceKey).withType(LISTENER_TYPE).withRetries(0).fail();
    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }

  private static void terminateElementByModification(
      final long processInstanceKey, final String elementId) {
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(getElementInstanceKey(processInstanceKey, elementId))
        .modify();
  }

  private static long getUserTaskKey(final long processInstanceKey) {
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }

  private static long getListenerJobKey(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(LISTENER_TYPE)
        .getFirst()
        .getKey();
  }

  private static long getElementInstanceKey(final long processInstanceKey, final String elementId) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(elementId)
        .getFirst()
        .getKey();
  }

  private static void assertThatElementIsTerminated(
      final long processInstanceKey, final String elementId) {
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(elementId)
                .exists())
        .describedAs("Expected element '%s' to be terminated", elementId)
        .isTrue();
  }

  private static void assertThatIncidentIsResolved(final long processInstanceKey) {
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .describedAs("Expected the incident of the canceling listener job to be resolved")
        .isTrue();
  }

  /**
   * Asserts that the user task and its listener job are canceled, and that commands for them are
   * rejected as not found, so that no state is left behind.
   */
  private static void assertThatCancelingUserTaskIsRemoved(final long processInstanceKey) {
    final long userTaskKey = getUserTaskKey(processInstanceKey);
    final long jobKey = getListenerJobKey(processInstanceKey);

    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .describedAs("Expected the user task to be canceled")
        .isTrue();
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withType(LISTENER_TYPE)
                .exists())
        .describedAs("Expected the canceling listener job to be canceled")
        .isTrue();

    final var assignRejection =
        ENGINE.userTask().withKey(userTaskKey).withAssignee("user").expectRejection().assign();
    assertThat(assignRejection.getRejectionType())
        .describedAs("Expected the user task to be gone")
        .isEqualTo(RejectionType.NOT_FOUND);
    final var completeRejection = ENGINE.job().withKey(jobKey).expectRejection().complete();
    assertThat(completeRejection.getRejectionType())
        .describedAs("Expected the canceling listener job to be gone")
        .isEqualTo(RejectionType.NOT_FOUND);
  }
}
