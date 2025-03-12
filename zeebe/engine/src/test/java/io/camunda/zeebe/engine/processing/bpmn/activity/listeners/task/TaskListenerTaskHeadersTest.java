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
import io.camunda.zeebe.engine.util.client.UserTaskClient;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import java.util.function.Consumer;
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
        userTask -> userTask.withAssignee("initial_assignee").claim());
  }

  @Test
  public void shouldIncludeUserTaskHeadersInJobCustomHeadersForCompletingTaskListener() {
    shouldIncludeUserTaskHeadersInJobCustomHeaders(
        ZeebeTaskListenerEventType.completing, UserTaskClient::complete);
  }

  @Test
  public void shouldIncludeUserTaskHeadersInJobCustomHeadersForUpdatingTaskListener() {
    shouldIncludeUserTaskHeadersInJobCustomHeaders(
        ZeebeTaskListenerEventType.updating, UserTaskClient::update);
  }

  private void shouldIncludeUserTaskHeadersInJobCustomHeaders(
      final ZeebeTaskListenerEventType eventType, final Consumer<UserTaskClient> userTaskAction) {
    final BpmnModelInstance processWithZeebeUserTask =
        helper.createProcessWithZeebeUserTask(
            taskBuilder ->
                taskBuilder
                    .zeebeTaskHeader("key", "value")
                    .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType)));

    // given
    final long processInstanceKey = helper.createProcessInstance(processWithZeebeUserTask);

    // when
    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // then
    final var activatedListenerJob = helper.activateJob(processInstanceKey, listenerType);

    assertThat(activatedListenerJob.getCustomHeaders()).contains(entry("key", "value"));
    helper.completeJobs(processInstanceKey, listenerType);
  }
}
