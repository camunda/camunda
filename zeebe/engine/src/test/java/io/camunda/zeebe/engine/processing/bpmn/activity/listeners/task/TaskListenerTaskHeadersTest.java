/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.task;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/** Tests verifying that a task listener may access user task headers via custom headers. */
public class TaskListenerTaskHeadersTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final TaskListenerTestHelper helper = new TaskListenerTestHelper(ENGINE);

  private String listenerType;

  @Before
  public void setup() {
    listenerType = "my_listener_" + UUID.randomUUID();
  }

  @Test
  public void shouldIncludeUserTaskHeadersInJobCustomHeadersForAssigningTaskListener() {
    shouldIncludeUserTaskHeadersInJobCustomHeaders(
        ZeebeTaskListenerEventType.assigning,
        pik -> ENGINE.userTask().ofInstance(pik).withAssignee("initial_assignee").claim());
  }

  @Test
  public void shouldIncludeUserTaskHeadersInJobCustomHeadersForCompletingTaskListener() {
    shouldIncludeUserTaskHeadersInJobCustomHeaders(
        ZeebeTaskListenerEventType.completing, pik -> ENGINE.userTask().ofInstance(pik).complete());
  }

  @Test
  public void shouldIncludeUserTaskHeadersInJobCustomHeadersForUpdatingTaskListener() {
    shouldIncludeUserTaskHeadersInJobCustomHeaders(
        ZeebeTaskListenerEventType.updating, pik -> ENGINE.userTask().ofInstance(pik).update());
  }

  @Test
  public void shouldIncludeUserTaskHeadersInJobCustomHeadersForCancelingTaskListener() {
    shouldIncludeUserTaskHeadersInJobCustomHeaders(
        ZeebeTaskListenerEventType.canceling,
        pik -> ENGINE.processInstance().withInstanceKey(pik).expectTerminating().cancel());
  }

  private void shouldIncludeUserTaskHeadersInJobCustomHeaders(
      final ZeebeTaskListenerEventType eventType, final Consumer<Long> transitionTrigger) {
    final BpmnModelInstance processWithZeebeUserTask =
        helper.createProcessWithZeebeUserTask(
            taskBuilder ->
                taskBuilder
                    .zeebeTaskHeader("key", "value")
                    .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType)));

    // given
    final long processInstanceKey = helper.createProcessInstance(processWithZeebeUserTask);

    // when: trigger the user task transition
    transitionTrigger.accept(processInstanceKey);

    // then
    final var activatedListenerJob = helper.activateJob(processInstanceKey, listenerType);

    assertThat(activatedListenerJob.getCustomHeaders()).contains(entry("key", "value"));
    helper.completeJobs(processInstanceKey, listenerType);
  }

  @Test
  public void shouldTrackVariablesAsChangedAttributeWhenTaskUpdateIsTriggeredByVariableUpdate() {
    // given: a process instance with a user task that has multiple `updating` listeners
    final var listenerTypes = new String[] {listenerType, listenerType + "_2", listenerType + "_3"};
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.updating, listenerTypes));

    final var userTaskInstanceKey = helper.getUserTaskElementInstanceKey(processInstanceKey);

    // when: updating task-scoped variables
    ENGINE
        .variables()
        .ofScope(userTaskInstanceKey)
        .withDocument(Map.of("var_name", "var_value"))
        .withLocalSemantic()
        .expectUpdating()
        .update();

    // then: verify all updating listener jobs receive "variables" as a changed attribute in headers
    final Consumer<JobRecordValue> assertChangedAttributesHeader =
        job ->
            assertThat(job.getCustomHeaders())
                .containsEntry(
                    Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME, "[\"variables\"]");

    Stream.of(listenerTypes)
        .forEach(
            listener -> {
              helper.assertActivatedJob(
                  processInstanceKey, listener, assertChangedAttributesHeader);
              helper.completeJobs(processInstanceKey, listener);
            });
  }
}
