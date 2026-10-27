/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ClaimUserTaskWithGlobalListenerTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String GLOBAL_LISTENER_TYPE = "globalAssigningListener";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldTriggerGlobalAssigningListenerWhenClaimingUserTask() {
    // given
    ENGINE
        .globalListener()
        .withId("GlobalUserTaskListener_Assigning")
        .withType(GLOBAL_LISTENER_TYPE)
        .withEventTypes(ZeebeTaskListenerEventType.assigning.name())
        .create();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when — claim the task
    ENGINE.userTask().withKey(userTaskKey).withAssignee("demo").claim();

    // then — global "assigning" listener job must be created
    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withJobKind(JobKind.TASK_LISTENER)
        .withJobListenerEventType(JobListenerEventType.ASSIGNING)
        .withType(GLOBAL_LISTENER_TYPE)
        .getFirst();
  }
}
