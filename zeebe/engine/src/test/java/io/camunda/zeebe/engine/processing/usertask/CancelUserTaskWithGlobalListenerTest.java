/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

public class CancelUserTaskWithGlobalListenerTest {

  private static final String PROCESS_ID = "process";
  private static final String GLOBAL_LISTENER_TYPE = "globalCancelingListener";
  private static final String LOCAL_LISTENER_TYPE = "localCancelingListener";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldTriggerGlobalCancelingListenerWhenUserTaskIsCanceled() {
    // given
    engine
        .globalListener()
        .withId("GlobalUserTaskListener_Canceling")
        .withType(GLOBAL_LISTENER_TYPE)
        .withEventTypes(ZeebeTaskListenerEventType.canceling.name())
        .create();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .boundaryEvent("signal-boundary")
                .signal("boundarySignal")
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    engine.signal().withSignalName("boundarySignal").broadcast();

    // then
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withJobKind(JobKind.TASK_LISTENER)
        .withJobListenerEventType(JobListenerEventType.CANCELING)
        .withType(GLOBAL_LISTENER_TYPE)
        .await();

    engine.job().ofInstance(processInstanceKey).withType(GLOBAL_LISTENER_TYPE).complete();

    RecordingExporter.userTaskRecords(UserTaskIntent.CANCELED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
  }

  @Test
  public void shouldTriggerGlobalAndLocalCancelingListenersInDefaultOrder() {
    // given
    engine
        .globalListener()
        .withId("GlobalUserTaskListener_Canceling")
        .withType(GLOBAL_LISTENER_TYPE)
        .withEventTypes(ZeebeTaskListenerEventType.canceling.name())
        .create();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask(
                    "task",
                    t ->
                        t.zeebeUserTask()
                            .zeebeTaskListener(l -> l.canceling().type(LOCAL_LISTENER_TYPE)))
                .boundaryEvent("signal-boundary")
                .signal("boundarySignal")
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    engine.signal().withSignalName("boundarySignal").broadcast();

    // then
    engine.job().ofInstance(processInstanceKey).withType(GLOBAL_LISTENER_TYPE).complete();

    engine.job().ofInstance(processInstanceKey).withType(LOCAL_LISTENER_TYPE).complete();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.TASK_LISTENER)
                .withJobListenerEventType(JobListenerEventType.CANCELING)
                .limit(2))
        .extracting(r -> r.getValue().getType())
        .containsExactly(GLOBAL_LISTENER_TYPE, LOCAL_LISTENER_TYPE);

    RecordingExporter.userTaskRecords(UserTaskIntent.CANCELED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
  }

  @Test
  public void shouldTriggerLocalThenGlobalCancelingListenerWhenGlobalConfiguredAfterNonGlobal() {
    // given
    engine
        .globalListener()
        .withId("GlobalUserTaskListener_Canceling")
        .withType(GLOBAL_LISTENER_TYPE)
        .withEventTypes(ZeebeTaskListenerEventType.canceling.name())
        .afterNonGlobal()
        .create();

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask(
                    "task",
                    t ->
                        t.zeebeUserTask()
                            .zeebeTaskListener(l -> l.canceling().type(LOCAL_LISTENER_TYPE)))
                .boundaryEvent("signal-boundary")
                .signal("boundarySignal")
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    engine.signal().withSignalName("boundarySignal").broadcast();

    // then
    engine.job().ofInstance(processInstanceKey).withType(LOCAL_LISTENER_TYPE).complete();

    engine.job().ofInstance(processInstanceKey).withType(GLOBAL_LISTENER_TYPE).complete();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.TASK_LISTENER)
                .withJobListenerEventType(JobListenerEventType.CANCELING)
                .limit(2))
        .extracting(r -> r.getValue().getType())
        .containsExactly(LOCAL_LISTENER_TYPE, GLOBAL_LISTENER_TYPE);

    RecordingExporter.userTaskRecords(UserTaskIntent.CANCELED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
  }
}
