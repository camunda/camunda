/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class UserTaskVariableUpdateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  private static final String USER_TASK_ELEMENT_ID = "my_user_task";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String listenerType;

  @Before
  public void setup() {
    listenerType = "my_listener_" + UUID.randomUUID();
  }

  @Test
  public void shouldRejectVariableUpdateWhenUserTaskIsNotInCreatedState() {
    // given: a process instance with a user task having an assignee and task listeners
    final long processInstanceKey =
        createProcessInstance(
            createProcessWithCamundaUserTask(
                t ->
                    t.zeebeAssignee("initial_assignee")
                        .zeebeTaskListener(l -> l.assigning().type(listenerType + "_assigning"))
                        .zeebeTaskListener(l -> l.updating().type(listenerType + "_updating"))));

    // since the user task has an initial assignee, the assignment transition is triggered
    final var assigningUserTaskRecord =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when: attempting to update variables while the user task is in the 'ASSIGNING' state
    final var variableUpdateRejection =
        ENGINE
            .variables()
            .ofScope(assigningUserTaskRecord.getValue().getElementInstanceKey())
            .withDocument(Map.of("employeeId", "E12345"))
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(variableUpdateRejection)
        .describedAs(
            "Expect rejection when trying to update variables for a user task that is currently in the 'ASSIGNING' state")
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasValueType(ValueType.VARIABLE_DOCUMENT)
        .hasRejectionReason(
            "Expected to trigger update transition for user task with key '%d', but it is in state 'ASSIGNING'"
                .formatted(assigningUserTaskRecord.getKey()));
  }

  @Test
  public void shouldUpdateVariablesWhenUserTaskIsInCreatedState() {
    // given: a process instance with a camunda user task
    final long processInstanceKey =
        createProcessInstanceWithVariables(
            createProcessWithCamundaUserTask(t -> t), Map.of("approvalStatus", "PENDING"));

    final var createdUserTaskRecord =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when: updating task-scoped variables
    final var variableUpdateRecord =
        ENGINE
            .variables()
            .ofScope(createdUserTaskRecord.getValue().getElementInstanceKey())
            .withDocument(Map.of("approvalStatus", "SUBMITTED"))
            .update();

    // then: variable update should be successful and trigger the user task update transition
    Assertions.assertThat(variableUpdateRecord)
        .describedAs("Expect variables to be successfully updated for a user task")
        .hasRecordType(RecordType.EVENT)
        .hasIntent(VariableDocumentIntent.UPDATED)
        .hasValueType(ValueType.VARIABLE_DOCUMENT);

    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(createdUserTaskRecord.getValue().getElementInstanceKey())
                .getFirst()
                .getValue())
        .describedAs("Expect the variable to be created at the local scope of user task element")
        .hasName("approvalStatus")
        .hasValue("\"SUBMITTED\"");

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.UPDATED))
        .extracting(Record::getIntent, r -> r.getValue().getChangedAttributes())
        .describedAs(
            "Expect the user task to pass the update transition with variables as a changed attribute")
        .containsSequence(
            tuple(UserTaskIntent.UPDATING, List.of(UserTaskRecord.VARIABLES)),
            tuple(UserTaskIntent.UPDATED, List.of(UserTaskRecord.VARIABLES)));
  }

  // utils
  private long createProcessInstance(final BpmnModelInstance modelInstance) {
    return createProcessInstanceWithVariables(modelInstance, Collections.emptyMap());
  }

  private long createProcessInstanceWithVariables(
      final BpmnModelInstance modelInstance, final Map<String, Object> processVariables) {
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withVariables(processVariables)
        .create();
  }

  private BpmnModelInstance createProcessWithCamundaUserTask(
      final UnaryOperator<UserTaskBuilder> userTaskBuilderFunction) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .userTask(USER_TASK_ELEMENT_ID, t -> userTaskBuilderFunction.apply(t.zeebeUserTask()))
        .endEvent()
        .done();
  }
}
