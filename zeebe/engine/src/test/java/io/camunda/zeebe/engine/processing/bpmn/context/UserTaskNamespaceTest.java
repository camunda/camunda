/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class UserTaskNamespaceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance BASE_MODEL =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("startEvent")
          .userTask("Task1")
          .zeebeUserTask()
          .zeebeOutputExpression("camunda.userTask.assignee", "task1Assignee")
          .zeebeOutputExpression("camunda.userTask.priority", "task1Priority")
          .userTask("Task2")
          .zeebeAssigneeExpression("task1Assignee")
          .zeebeUserTask()
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldMapTheProcessInstanceKey() {
    ENGINE.deployment().withXmlResource(BASE_MODEL).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var task1 =
        ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("User1").claim();
    ENGINE.userTask().withKey(task1.getKey()).complete();

    assertThat(RecordingExporter.processInstanceRecords().limitToProcessInstanceCompleted().count())
        .isGreaterThan(0);

    final var userTask2 =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED).skip(1).getFirst();
    assertThat(userTask2.getValue().getAssignee()).isEqualTo("User1");

    ENGINE.userTask().ofInstance(processInstanceKey).withKey(userTask2.getKey()).complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .filterRootScope()
                .limitToProcessInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(ProcessInstanceIntent.ELEMENT_COMPLETED);
  }
}
