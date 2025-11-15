/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.system.configuration.engine.GlobalListenerCfg;
import io.camunda.zeebe.broker.system.configuration.engine.GlobalListenersCfg;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Suppressing resource warning because JobWorker instances are managed and closed automatically
// by `CamundaClient#close()`, which is invoked via the JUnit 5 extension due to the `client`
// being annotated with `@AutoClose`. This ensures proper cleanup without requiring explicit
// try-with-resources statements in each test.
@SuppressWarnings("resource")
@ZeebeIntegration
public class GlobalUserTaskListenersTest {

  @TestZeebe(autoStart = false)
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient camundaClient;

  private ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void init() {
    camundaClient =
        ZEEBE
            .newClientBuilder()
            .useDefaultRetryPolicy(true) // needed to avoid errors after broker restarts
            .build();
    resourcesHelper = new ZeebeResourcesHelper(camundaClient);
  }

  @Test
  public void shouldExecuteBothGlobalAndNonGlobalListenersInCorrectOrder() {
    // given: global listener configuration and process definition

    // configure global listeners
    configureBroker(
        List.of(
            createListenerConfig("globalBefore1", List.of("creating"), false),
            createListenerConfig("globalAfter1", List.of("creating"), true),
            createListenerConfig("globalBefore2", List.of("creating"), false)));

    // setup workers for listeners (global and non-global)
    setupAutocompleteWorker("globalBefore1");
    setupAutocompleteWorker("globalBefore2");
    setupAutocompleteWorker("globalAfter1");
    setupAutocompleteWorker("local1");
    setupAutocompleteWorker("local2");

    // deploy process definition
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("processWithUserTask")
            .startEvent("start")
            .userTask(
                "task",
                t ->
                    t.zeebeUserTask()
                        .zeebeTaskListener(l -> l.creating().type("local1"))
                        .zeebeTaskListener(l -> l.creating().type("local2")))
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(processDefinition);

    // when: process with task is created
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);
    final var userTask = getTaskInfo("task", processInstanceKey);

    // then: all listeners are executed in correct order
    final List<String> executedListeners =
        searchListenerJobTypes(userTask.elementKey(), JobListenerEventType.CREATING);
    assertThat(executedListeners)
        .containsExactly("globalBefore1", "globalBefore2", "local1", "local2", "globalAfter1");
  }

  @Test
  public void shouldApplyConfigurationChangeToExistingProcessInstancesWithoutRedeployment() {
    // given: global listener configuration and process already started

    // configure global listeners
    configureBroker(List.of(createListenerConfig("oldAssigning", List.of("assigning"), false)));

    // setup workers for listeners (global and non-global)
    setupAutocompleteWorker("oldAssigning");
    setupAutocompleteWorker("newAssigning");

    // deploy process definition
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("processWithUserTask")
            .startEvent("start")
            .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(processDefinition);

    // start process and create task
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);
    final var userTask = getTaskInfo("task", processInstanceKey);

    // verify that old listener is executed
    camundaClient.newAssignUserTaskCommand(userTask.taskKey()).assignee("user1").send().join();
    assertCompletedListeners(
        userTask.elementKey(), JobListenerEventType.ASSIGNING, List.of("oldAssigning"));

    // when: configuration is changed and broker restarted
    configureBroker(
        List.of(
            createListenerConfig("newAssigning", List.of("assigning"), false),
            createListenerConfig("oldAssigning", List.of("assigning"), false)));

    // then: new configuration is used for new commands to existing process instance
    camundaClient.newAssignUserTaskCommand(userTask.taskKey()).assignee("user2").send().join();
    assertCompletedListeners(
        userTask.elementKey(),
        JobListenerEventType.ASSIGNING,
        List.of(
            "oldAssigning", // from old command
            "newAssigning", // from new command
            "oldAssigning")); // from new command
  }

  @Test
  public void shouldApplyConfigurationChangeToNewProcessInstancesWithoutRedeployment() {
    // given: global listener configuration and process already started

    // configure global listeners
    configureBroker(List.of(createListenerConfig("oldCreating", List.of("creating"), false)));

    // setup workers for listeners (global and non-global)
    setupAutocompleteWorker("oldCreating");
    setupAutocompleteWorker("newCreating");

    // deploy process definition
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("processWithUserTask")
            .startEvent("start")
            .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(processDefinition);

    // when: configuration is changed and broker restarted
    configureBroker(
        List.of(
            createListenerConfig("newCreating", List.of("Creating"), false),
            createListenerConfig("oldCreating", List.of("Creating"), false)));

    // then: new configuration is applied for new processes
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);
    final var userTask = getTaskInfo("task", processInstanceKey);
    assertCompletedListeners(
        userTask.elementKey(),
        JobListenerEventType.CREATING,
        List.of("newCreating", "oldCreating"));
  }

  private void setupAutocompleteWorker(final String jobType) {
    camundaClient
        .newWorker()
        .jobType(jobType)
        .handler(
            (client, job) -> {
              client.newCompleteCommand(job).send().join();
            })
        .open();
  }

  private void configureBroker(final List<GlobalListenerCfg> listenerCfgs) {
    final GlobalListenersCfg globalListenersCfg = new GlobalListenersCfg();
    globalListenersCfg.setUserTask(listenerCfgs);
    ZEEBE.brokerConfig().getExperimental().getEngine().setGlobalListeners(globalListenersCfg);
    if (ZEEBE.isStarted()) {
      ZEEBE.stop();
    }
    ZEEBE.start();
    ZEEBE.awaitCompleteTopology();
  }

  private GlobalListenerCfg createListenerConfig(
      final String type, final List<String> eventTypes, final boolean afterNonGlobal) {
    final GlobalListenerCfg listenerCfg = new GlobalListenerCfg();
    listenerCfg.setType(type);
    listenerCfg.setEventTypes(eventTypes);
    listenerCfg.setAfterNonGlobal(afterNonGlobal);
    return listenerCfg;
  }

  private List<String> searchListenerJobTypes(
      final long taskKey, final JobListenerEventType eventType) {
    return RecordingExporter.jobRecords(JobIntent.COMPLETED)
        .withoutDuplicatedPositions()
        .map(Record::getValue)
        .filter(j -> j.getElementInstanceKey() == taskKey)
        .filter(j -> j.getJobKind() == JobKind.TASK_LISTENER)
        .filter(j -> j.getJobListenerEventType() == eventType)
        .map(JobRecordValue::getType)
        .toList();
  }

  private void assertCompletedListeners(
      final long taskKey, final JobListenerEventType eventType, final List<String> expectedTypes) {
    final List<String> completedTypes = searchListenerJobTypes(taskKey, eventType);
    assertThat(completedTypes)
        .as("Completed listener types for event " + eventType)
        .containsExactlyElementsOf(expectedTypes);
  }

  private TaskInfo getTaskInfo(final String taskName, final long processInstanceKey) {
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .filter(r -> processInstanceKey == r.getValue().getProcessInstanceKey())
        .filter(r -> r.getValue().getElementId().equals(taskName))
        .map(Record::getValue)
        .map(r -> new TaskInfo(r.getUserTaskKey(), r.getElementInstanceKey()))
        .findFirst()
        .orElseThrow();
  }

  private record TaskInfo(long taskKey, long elementKey) {}
}
