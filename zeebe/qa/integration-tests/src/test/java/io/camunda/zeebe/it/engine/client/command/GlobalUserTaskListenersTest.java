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
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import io.camunda.zeebe.broker.system.configuration.engine.GlobalListenerCfg;
import io.camunda.zeebe.broker.system.configuration.engine.GlobalListenersCfg;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
    configureGlobalListeners(
        List.of(
            createListenerConfig("globalBefore1", List.of("creating"), false),
            createListenerConfig("globalAfter1", List.of("creating"), true),
            createListenerConfig("globalBefore2", List.of("creating"), false)));
    restartBroker();

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
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // then: all listeners are executed in correct order
    assertThat(
            RecordingExporter.records()
                .skipUntil(
                    r -> // skip until task creation is started
                    r.getIntent() == UserTaskIntent.CREATING
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey)
                .limit(
                    r -> // stop after task creation has been completed
                    r.getIntent() == UserTaskIntent.CREATED
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey)
                // get all completed task listener jobs
                .jobRecords()
                .withJobKind(JobKind.TASK_LISTENER)
                .withIntent(JobIntent.COMPLETED))
        .extracting(Record::getValue)
        // check that all jobs are for creating event
        .allMatch(job -> job.getJobListenerEventType() == JobListenerEventType.CREATING)
        .extracting(JobRecordValue::getType)
        // check correct listeners have been executed in correct order
        .containsExactly("globalBefore1", "globalBefore2", "local1", "local2", "globalAfter1");
  }

  @Test
  public void shouldApplyConfigurationChangeToExistingProcessInstancesWithoutRedeployment() {
    // given: global listener configuration and process already started

    // configure global listeners
    configureGlobalListeners(
        List.of(createListenerConfig("oldAssigning", List.of("assigning"), false)));
    restartBroker();

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
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst()
            .getValue();

    // verify that old listener is executed
    camundaClient
        .newAssignUserTaskCommand(userTask.getUserTaskKey())
        .assignee("user1")
        .send()
        .join();
    assertThat(
            RecordingExporter.records()
                .skipUntil(
                    r -> // skip until task assignment is started
                    r.getIntent() == UserTaskIntent.ASSIGNING
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey)
                .limit(
                    r -> // stop after task assignment has been completed
                    r.getIntent() == UserTaskIntent.ASSIGNED
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey)
                // get all completed task listener jobs
                .jobRecords()
                .withJobKind(JobKind.TASK_LISTENER)
                .withIntent(JobIntent.COMPLETED))
        .extracting(Record::getValue)
        // check that all jobs are for assigning event
        .allMatch(job -> job.getJobListenerEventType() == JobListenerEventType.ASSIGNING)
        .extracting(JobRecordValue::getType)
        // check correct listeners have been executed in correct order
        .containsExactly("oldAssigning");

    // when: configuration is changed and broker restarted
    configureGlobalListeners(
        List.of(
            createListenerConfig("newAssigning", List.of("assigning"), false),
            createListenerConfig("oldAssigning", List.of("assigning"), false)));
    restartBroker();

    // then: new configuration is used for new commands to existing process instance
    camundaClient
        .newAssignUserTaskCommand(userTask.getUserTaskKey())
        .assignee("user2")
        .send()
        .join();

    assertThat(
            RecordingExporter.records()
                .skipUntil(
                    r -> // skip until task assignment is started
                    r.getIntent() == UserTaskIntent.ASSIGNING
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey
                            && ((UserTaskRecordValue) r.getValue()).getAssignee().equals("user2"))
                .limit(
                    r -> // stop after task assignment has been completed
                    r.getIntent() == UserTaskIntent.ASSIGNED
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey
                            && ((UserTaskRecordValue) r.getValue()).getAssignee().equals("user2"))
                // get all completed task listener jobs
                .jobRecords()
                .withJobKind(JobKind.TASK_LISTENER)
                .withIntent(JobIntent.COMPLETED))
        .extracting(Record::getValue)
        // check that all jobs are for assigning event
        .allMatch(job -> job.getJobListenerEventType() == JobListenerEventType.ASSIGNING)
        .extracting(JobRecordValue::getType)
        // check correct listeners have been executed in correct order
        .containsExactly("newAssigning", "oldAssigning");
  }

  @Test
  public void shouldApplyConfigurationChangeToNewProcessInstancesWithoutRedeployment() {
    // given: global listener configuration and process already started

    // configure global listeners
    configureGlobalListeners(
        List.of(createListenerConfig("oldCreating", List.of("creating"), false)));
    restartBroker();

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
    configureGlobalListeners(
        List.of(
            createListenerConfig("newCreating", List.of("creating"), false),
            createListenerConfig("oldCreating", List.of("creating"), false)));
    restartBroker();

    // then: new configuration is applied for new processes
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);
    assertThat(
            RecordingExporter.records()
                .skipUntil(
                    r -> // skip until task creation is started
                    r.getIntent() == UserTaskIntent.CREATING
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey)
                .limit(
                    r -> // stop after task creation has been completed
                    r.getIntent() == UserTaskIntent.CREATED
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey)
                // get all completed task listener jobs
                .jobRecords()
                .withJobKind(JobKind.TASK_LISTENER)
                .withIntent(JobIntent.COMPLETED))
        .extracting(Record::getValue)
        // check that all jobs are for creating event
        .allMatch(job -> job.getJobListenerEventType() == JobListenerEventType.CREATING)
        .extracting(JobRecordValue::getType)
        // check correct listeners have been executed in correct order
        .containsExactly("newCreating", "oldCreating");
  }

  @Test
  public void shouldApplyOldConfigurationToLifecycleEventStartedBeforeRestart() {
    // given: global listener configuration and process already started

    // configure global listeners
    configureGlobalListeners(
        List.of(createListenerConfig("oldCreating", List.of("creating"), false)));
    restartBroker();

    // setup workers for listeners (worker for old listener is paused to simulate long-running task)
    final CountDownLatch workerLatch = new CountDownLatch(1);
    camundaClient
        .newWorker()
        .jobType("oldCreating")
        .handler(
            (client, job) -> {
              workerLatch.await();
              client.newCompleteCommand(job).send().join();
            })
        .open();
    setupAutocompleteWorker("newCreating");

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

    // when: configuration is changed and broker restarted
    configureGlobalListeners(
        List.of(
            createListenerConfig("oldCreating", List.of("creating"), false),
            createListenerConfig("newCreating", List.of("creating"), false)));
    restartBroker();

    // then: old configuration is used for lifecycle event started before restart

    // wait for new configuration to be applied and check that listener has not been completed yet
    assertThat(
            RecordingExporter.records()
                // skip until task creation is started
                .skipUntil(r -> r.getIntent() == UserTaskIntent.CREATING)
                // stop when new global listener configuration is applied
                .limit(r -> r.getIntent() == GlobalListenerBatchIntent.CONFIGURED)
                // check if any task listener job has been completed
                .jobRecords()
                .withJobKind(JobKind.TASK_LISTENER)
                .withIntent(JobIntent.COMPLETED))
        .isEmpty();

    // unpause worker to allow completing the old listener job
    workerLatch.countDown();

    // check that only old listener has been executed
    assertThat(
            RecordingExporter.records()
                .limit(
                    r -> // stop after task creation has been completed
                    r.getIntent() == UserTaskIntent.CREATED
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey)
                // get all completed task listener jobs
                .jobRecords()
                .withJobKind(JobKind.TASK_LISTENER)
                .withIntent(JobIntent.COMPLETED))
        .extracting(Record::getValue)
        // check that all jobs are for creating event
        .allMatch(job -> job.getJobListenerEventType() == JobListenerEventType.CREATING)
        .extracting(JobRecordValue::getType)
        // check correct listeners have been executed in correct order
        .containsExactly("oldCreating");
  }

  @Test
  void shouldMergeConfigurationAndApiGlobalTaskListener() {
    // given: a process definition and global listeners defined through configuration and API

    // configure global listeners
    configureGlobalListeners(
        List.of(
            createListenerConfig("10_configuration", List.of("creating"), false),
            createListenerConfig("20_configuration", List.of("creating"), false)));
    restartBroker();

    // add global listener via API (note that no restart is needed)
    camundaClient
        .newCreateGlobalTaskListenerRequest()
        .id("15_api")
        .type("15_api")
        .eventTypes(GlobalTaskListenerEventType.CREATING)
        .send()
        .join();

    // setup workers for listeners
    setupAutocompleteWorker("10_configuration");
    setupAutocompleteWorker("20_configuration");
    setupAutocompleteWorker("15_api");

    // deploy process definition
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("processWithUserTask")
            .startEvent("start")
            .userTask("task")
            .zeebeUserTask()
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(processDefinition);

    // when: process with task is created
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // then: all listeners are executed in correct order
    assertThat(
            RecordingExporter.records()
                .skipUntil(
                    r -> // skip until task creation is started
                    r.getIntent() == UserTaskIntent.CREATING
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey)
                .limit(
                    r -> // stop after task creation has been completed
                    r.getIntent() == UserTaskIntent.CREATED
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey)
                // get all completed task listener jobs
                .jobRecords()
                .withJobKind(JobKind.TASK_LISTENER)
                .withIntent(JobIntent.COMPLETED))
        .extracting(Record::getValue)
        // check that all jobs are for creating event
        .allMatch(job -> job.getJobListenerEventType() == JobListenerEventType.CREATING)
        .extracting(JobRecordValue::getType)
        // check correct listeners have been executed in correct order
        .containsExactly("10_configuration", "15_api", "20_configuration");
  }

  @Test
  void shouldReplaceOnlyConfigurationDefinedGlobalTaskListenersAfterRestart() {
    // given: a process definition and global listeners defined through configuration and API

    // configure "old" global listeners
    configureGlobalListeners(
        List.of(
            createListenerConfig("10_configuration", List.of("creating"), false),
            createListenerConfig("20_configuration", List.of("assigning"), false)));
    restartBroker();

    // add global listener via API (note that no restart is needed)
    camundaClient
        .newCreateGlobalTaskListenerRequest()
        .id("15_api")
        .type("15_api")
        .eventTypes(GlobalTaskListenerEventType.CREATING)
        .send()
        .join();
    camundaClient
        .newCreateGlobalTaskListenerRequest()
        .id("30_api")
        .type("30_api")
        .eventTypes(GlobalTaskListenerEventType.CREATING)
        .send()
        .join();

    // setup workers for listeners
    setupAutocompleteWorker("10_configuration");
    setupAutocompleteWorker("20_configuration");
    setupAutocompleteWorker("15_api");
    setupAutocompleteWorker("30_api");

    // deploy process definition
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("processWithUserTask")
            .startEvent("start")
            .userTask("task")
            .zeebeUserTask()
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(processDefinition);

    // when: the configuration is changed and the cluster restarted

    // configure "new" global listeners
    configureGlobalListeners(
        List.of(
            // 10_configuration removed
            // 20_configuration is still there but with different event type
            createListenerConfig("20_configuration", List.of("creating"), false),
            // 01_configuration added
            createListenerConfig("40_configuration", List.of("creating"), false)));
    restartBroker();

    // setup worker for new listener
    setupAutocompleteWorker("40_configuration");

    // then: the correct listeners are executed, replacing old configuration-defined listeners
    //       with the new ones while keeping the API-defined listeners intact
    // Expected listeners:
    // 15_api (API, should not be replaced)
    // 20_configuration (old configuration but with updated event type)
    // 30_api (API, should not be replaced)
    // 40_configuration (new configuration)
    // Note: 10_configuration should not be executed as it has been removed from the configuration
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);
    assertThat(
            RecordingExporter.records()
                .skipUntil(
                    r -> // skip until task creation is started
                    r.getIntent() == UserTaskIntent.CREATING
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey)
                .limit(
                    r -> // stop after task creation has been completed
                    r.getIntent() == UserTaskIntent.CREATED
                            && ((UserTaskRecordValue) r.getValue()).getProcessInstanceKey()
                                == processInstanceKey)
                // get all completed task listener jobs
                .jobRecords()
                .withJobKind(JobKind.TASK_LISTENER)
                .withIntent(JobIntent.COMPLETED))
        .extracting(Record::getValue)
        // check that all jobs are for creating event
        .allMatch(job -> job.getJobListenerEventType() == JobListenerEventType.CREATING)
        .extracting(JobRecordValue::getType)
        // check correct listeners have been executed in correct order
        .containsExactly("15_api", "20_configuration", "30_api", "40_configuration");
  }

  @Test
  public void shouldAllowCancellingUserTaskWhileWaitingForListener() {
    // given: global listener configuration and a process with a task
    // a listener is configured for the "creating" event but no associated worker is present
    configureGlobalListeners(List.of(createListenerConfig("global", List.of("creating"), false)));
    restartBroker();

    // deploy process definition
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("processWithUserTask")
            .startEvent("start")
            .userTask("task")
            .zeebeUserTask()
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(processDefinition);

    // create process instance
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // when: the process is canceled while waiting for the listener

    // Wait for the listener job to be created
    // Note that the listener will never complete because no related job worker has been registered.
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType("global")
        .await();

    // Cancel the process (with the task)
    camundaClient.newCancelInstanceCommand(processInstanceKey).send();

    // then: the "creating" job and the task are correctly canceled
    RecordingExporter.userTaskRecords(UserTaskIntent.CANCELED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    RecordingExporter.jobRecords(JobIntent.CANCELED)
        .withProcessInstanceKey(processInstanceKey)
        .withJobListenerEventType(JobListenerEventType.CREATING)
        .withType("global")
        .await();
  }

  private void setupAutocompleteWorker(final String jobType) {
    camundaClient
        .newWorker()
        .jobType(jobType)
        .handler((client, job) -> client.newCompleteCommand(job).send().join())
        .open();
  }

  private void configureGlobalListeners(final List<GlobalListenerCfg> listenerCfgs) {
    final GlobalListenersCfg globalListenersCfg = new GlobalListenersCfg();
    globalListenersCfg.setUserTask(listenerCfgs);
    ZEEBE.unifiedConfig().getCluster().setGlobalListeners(globalListenersCfg);
  }

  private void restartBroker() {
    if (ZEEBE.isStarted()) {
      ZEEBE.stop();
      RecordingExporter.reset();
    }
    ZEEBE.start();
    ZEEBE.awaitCompleteTopology();
  }

  private GlobalListenerCfg createListenerConfig(
      final String type, final List<String> eventTypes, final boolean afterNonGlobal) {
    final GlobalListenerCfg listenerCfg = new GlobalListenerCfg();
    listenerCfg.setId(type);
    listenerCfg.setType(type);
    listenerCfg.setEventTypes(eventTypes);
    listenerCfg.setAfterNonGlobal(afterNonGlobal);
    return listenerCfg;
  }
}
