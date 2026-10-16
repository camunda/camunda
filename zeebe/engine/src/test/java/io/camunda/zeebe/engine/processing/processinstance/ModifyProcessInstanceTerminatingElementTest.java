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
    final long userTaskKey = getUserTaskKey(processInstanceKey);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(getElementInstanceKey(processInstanceKey, "userTask"))
        .modify();

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .describedAs("Expected the canceling user task to be canceled")
        .isTrue();

    final var rejection =
        ENGINE.userTask().withKey(userTaskKey).withAssignee("user").expectRejection().assign();
    assertThat(rejection.getRejectionType())
        .describedAs("Expected the canceled user task to be gone")
        .isEqualTo(RejectionType.NOT_FOUND);
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
    ENGINE.job().ofInstance(processInstanceKey).withType(LISTENER_TYPE).withRetries(0).fail();
    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();

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

  private static long getUserTaskKey(final long processInstanceKey) {
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
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
}
