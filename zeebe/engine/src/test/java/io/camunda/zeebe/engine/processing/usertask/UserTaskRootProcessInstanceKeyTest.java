/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Set;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class UserTaskRootProcessInstanceKeyTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String CHILD_PROCESS_ID = "child-process";
  private static final String GRANDCHILD_PROCESS_ID = "grandchild-process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance simpleProcessWithUserTask() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .userTask("task")
        .zeebeUserTask()
        .endEvent()
        .done();
  }

  private static BpmnModelInstance parentProcessWithCallActivity() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .callActivity("call", c -> c.zeebeProcessId(CHILD_PROCESS_ID))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance childProcessWithUserTask() {
    return Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
        .startEvent()
        .userTask("child-task")
        .zeebeUserTask()
        .endEvent()
        .done();
  }

  private static BpmnModelInstance childProcessWithCallActivity() {
    return Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
        .startEvent()
        .callActivity("child-call", c -> c.zeebeProcessId(GRANDCHILD_PROCESS_ID))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance grandchildProcessWithUserTask() {
    return Bpmn.createExecutableProcess(GRANDCHILD_PROCESS_ID)
        .startEvent()
        .userTask("grandchild-task")
        .zeebeUserTask()
        .endEvent()
        .done();
  }

  @Test
  public void shouldSetRootProcessInstanceKeyToProcessInstanceKeyForSimpleProcess() {
    // given
    ENGINE.deployment().withXmlResource(simpleProcessWithUserTask()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<UserTaskRecordValue> userTaskCreated =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(userTaskCreated.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // rootProcessInstanceKey should equal processInstanceKey for simple processes
    assertThat(userTaskCreated.getValue().getRootProcessInstanceKey())
        .isEqualTo(processInstanceKey);
  }

  @Test
  public void shouldSetRootProcessInstanceKeyToTopLevelParentForNestedCallActivities() {
    // given
    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcessWithCallActivity())
        .withXmlResource("child.bpmn", childProcessWithCallActivity())
        .withXmlResource("grandchild.bpmn", grandchildProcessWithUserTask())
        .deploy();

    // when
    final long rootProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then - get the child process instance
    final Record<ProcessInstanceRecordValue> childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(rootProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final long childProcessInstanceKey = childProcessInstance.getKey();

    // get the grandchild process instance
    final Record<ProcessInstanceRecordValue> grandchildProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(childProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final long grandchildProcessInstanceKey = grandchildProcessInstance.getKey();

    // Verify all three process instances are different
    assertThat(
            Set.of(rootProcessInstanceKey, childProcessInstanceKey, grandchildProcessInstanceKey))
        .hasSize(3);

    // get the user task created in the grandchild process
    final Record<UserTaskRecordValue> userTaskCreated =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(grandchildProcessInstanceKey)
            .getFirst();

    Assertions.assertThat(userTaskCreated.getValue())
        .hasProcessInstanceKey(grandchildProcessInstanceKey)
        .hasRootProcessInstanceKey(rootProcessInstanceKey);
  }

  @Test
  public void shouldPropagateRootProcessInstanceKeyThroughAllUserTaskIntents() {
    // given
    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcessWithCallActivity())
        .withXmlResource("child.bpmn", childProcessWithUserTask())
        .deploy();

    // when
    final long rootProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<ProcessInstanceRecordValue> childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(rootProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    final long childProcessInstanceKey = childProcessInstance.getKey();

    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(childProcessInstanceKey)
            .getFirst()
            .getKey();

    // Complete the user task
    ENGINE.userTask().withKey(userTaskKey).complete();

    // then - verify all user task events have the correct rootProcessInstanceKey
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(childProcessInstanceKey)
                .limit(4))
        .extracting(Record::getIntent, r -> r.getValue().getRootProcessInstanceKey())
        .containsExactly(
            tuple(UserTaskIntent.CREATING, rootProcessInstanceKey),
            tuple(UserTaskIntent.CREATED, rootProcessInstanceKey),
            tuple(UserTaskIntent.COMPLETING, rootProcessInstanceKey),
            tuple(UserTaskIntent.COMPLETED, rootProcessInstanceKey));
  }
}
