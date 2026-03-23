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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for global execution listeners configured via broker configuration. Tests
 * end-to-end behavior including ordering with BPMN-level execution listeners, config changes on
 * restart, and version pinning for running instances.
 *
 * <p>Mirrors the structure of {@link GlobalUserTaskListenersTest} but for execution listeners.
 */
@SuppressWarnings("resource")
@ZeebeIntegration
public class GlobalExecutionListenersTest {

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
  void shouldExecuteBothGlobalAndBpmnExecutionListenersInCorrectOrder() {
    // given: global execution listener configuration and process with BPMN-level EL

    // configure global execution listeners (beforeNonGlobal by default)
    configureGlobalExecutionListeners(
        List.of(
            createListenerConfig(
                "globalBefore1", "globalBefore1", List.of("start"), List.of("serviceTask"), false),
            createListenerConfig(
                "globalAfter1", "globalAfter1", List.of("start"), List.of("serviceTask"), true),
            createListenerConfig(
                "globalBefore2",
                "globalBefore2",
                List.of("start"),
                List.of("serviceTask"),
                false)));
    restartBroker();

    // setup workers for listeners (global and BPMN-level)
    setupAutocompleteWorker("globalBefore1");
    setupAutocompleteWorker("globalBefore2");
    setupAutocompleteWorker("globalAfter1");
    setupAutocompleteWorker("bpmnStartEL");

    // deploy process with a BPMN-level start execution listener on the service task
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("processWithServiceTask")
            .startEvent("start")
            .serviceTask(
                "task",
                t -> t.zeebeJobType("serviceTaskJob").zeebeStartExecutionListener("bpmnStartEL"))
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(processDefinition);

    // setup worker for the actual service task
    setupAutocompleteWorker("serviceTaskJob");

    // when: a process instance is created
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // then: all listeners are executed in correct order
    // Expected order: globalBefore1, globalBefore2, bpmnStartEL (non-global), globalAfter1
    assertThat(
            RecordingExporter.records()
                .skipUntil(
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATING
                            && r.getValue() instanceof ProcessInstanceRecordValue pirv
                            && pirv.getProcessInstanceKey() == processInstanceKey
                            && pirv.getBpmnElementType() == BpmnElementType.SERVICE_TASK)
                .limit(
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED
                            && r.getValue() instanceof ProcessInstanceRecordValue pirv
                            && pirv.getProcessInstanceKey() == processInstanceKey
                            && pirv.getBpmnElementType() == BpmnElementType.SERVICE_TASK)
                .jobRecords()
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED))
        .extracting(Record::getValue)
        .allMatch(job -> job.getJobListenerEventType() == JobListenerEventType.START)
        .extracting(JobRecordValue::getType)
        .containsExactly("globalBefore1", "globalBefore2", "bpmnStartEL", "globalAfter1");
  }

  @Test
  void shouldApplyNewConfigToNewProcessInstancesAfterRestart() {
    // given: initial global execution listener configuration
    configureGlobalExecutionListeners(
        List.of(
            createListenerConfig(
                "oldStart", "oldStart", List.of("start"), List.of("serviceTask"), false)));
    restartBroker();

    setupAutocompleteWorker("oldStart");
    setupAutocompleteWorker("newStart");

    // deploy process definition
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("processWithServiceTask")
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeJobType("serviceTaskJob"))
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(processDefinition);

    // when: the configuration is changed and the cluster restarted
    configureGlobalExecutionListeners(
        List.of(
            createListenerConfig(
                "newStart", "newStart", List.of("start"), List.of("serviceTask"), false)));
    restartBroker();

    setupAutocompleteWorker("serviceTaskJob");

    // then: a new process instance uses the new configuration
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    assertThat(
            RecordingExporter.records()
                .skipUntil(
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATING
                            && r.getValue() instanceof ProcessInstanceRecordValue pirv
                            && pirv.getProcessInstanceKey() == processInstanceKey
                            && pirv.getBpmnElementType() == BpmnElementType.SERVICE_TASK)
                .limit(
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED
                            && r.getValue() instanceof ProcessInstanceRecordValue pirv
                            && pirv.getProcessInstanceKey() == processInstanceKey
                            && pirv.getBpmnElementType() == BpmnElementType.SERVICE_TASK)
                .jobRecords()
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED))
        .extracting(Record::getValue)
        .allMatch(job -> job.getJobListenerEventType() == JobListenerEventType.START)
        .extracting(JobRecordValue::getType)
        .containsExactly("newStart");
  }

  @Test
  void shouldApplyOldConfigToExistingInstancesWhenConfigChanges() {
    // given: global execution listener on "end" event for service tasks
    configureGlobalExecutionListeners(
        List.of(
            createListenerConfig(
                "oldEnd", "oldEnd", List.of("end"), List.of("serviceTask"), false)));
    restartBroker();

    setupAutocompleteWorker("oldEnd");
    setupAutocompleteWorker("newEnd");

    // deploy process definition
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("processWithServiceTask")
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeJobType("serviceTaskJob"))
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(processDefinition);

    // create a process instance BEFORE config change — service task pins the current config
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // wait for the service task job to be created (service task is now activated with pinned
    // config)
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType("serviceTaskJob")
        .getFirst();

    // when: the configuration is changed and the cluster is restarted
    configureGlobalExecutionListeners(
        List.of(
            createListenerConfig(
                "newEnd", "newEnd", List.of("end"), List.of("serviceTask"), false)));
    restartBroker();

    // complete the service task job — the "end" execution listener should use the old config
    camundaClient
        .newWorker()
        .jobType("serviceTaskJob")
        .handler((client, job) -> client.newCompleteCommand(job).send().join())
        .open();

    // then: the old "end" listener config ("oldEnd") is used because it was pinned at activation
    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .filter(r -> r.getValue().getElementId().equals("task"))
                .getFirst())
        .extracting(Record::getValue)
        .satisfies(
            job -> {
              assertThat(job.getType()).isEqualTo("oldEnd");
              assertThat(job.getJobListenerEventType()).isEqualTo(JobListenerEventType.END);
            });
  }

  @Test
  void shouldExecuteGlobalListenerOnProcessStartAndEndEvents() {
    // given: global listener for process start and end events
    configureGlobalExecutionListeners(
        List.of(
            createListenerConfig(
                "processLifecycle",
                "processLifecycle",
                List.of("start", "end"),
                List.of("process"),
                false)));
    restartBroker();

    setupAutocompleteWorker("processLifecycle");
    setupAutocompleteWorker("serviceTaskJob");

    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("processLifecycleTest")
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeJobType("serviceTaskJob"))
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(processDefinition);

    // when: process instance is created and completes
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // then: both start and end execution listener jobs are created for the process element
    assertThat(
            RecordingExporter.jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withType("processLifecycle")
                .limit(2))
        .extracting(Record::getValue)
        .extracting(JobRecordValue::getJobListenerEventType)
        .containsExactly(JobListenerEventType.START, JobListenerEventType.END);
  }

  @Test
  void shouldFilterByElementTypeAndOnlyFireOnMatchingElements() {
    // given: global listener scoped to serviceTask only
    configureGlobalExecutionListeners(
        List.of(
            createListenerConfig(
                "serviceOnly", "serviceOnly", List.of("start"), List.of("serviceTask"), false)));
    restartBroker();

    setupAutocompleteWorker("serviceOnly");
    setupAutocompleteWorker("serviceTaskJob");

    // process has both a service task and a manual end event
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("filterByElementTypeTest")
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeJobType("serviceTaskJob"))
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(processDefinition);

    // when: process instance runs to completion
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // wait for process to complete
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();

    // then: execution listener only fired on the service task element, not on start/end events
    final var elJobs =
        RecordingExporter.jobRecords(JobIntent.COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType("serviceOnly")
            .limit(1)
            .toList();
    assertThat(elJobs)
        .hasSize(1)
        .first()
        .extracting(r -> r.getValue().getElementId())
        .isEqualTo("task");
  }

  @Test
  void shouldExpandCategoryToMatchingElementTypes() {
    // given: global listener using "tasks" category (covers all task types)
    final GlobalListenerCfg cfg = new GlobalListenerCfg();
    cfg.setId("tasksCategory");
    cfg.setType("tasksCategory");
    cfg.setEventTypes(List.of("start"));
    cfg.setCategories(List.of("tasks"));
    cfg.setAfterNonGlobal(false);

    configureGlobalExecutionListeners(List.of(cfg));
    restartBroker();

    setupAutocompleteWorker("tasksCategory");
    setupAutocompleteWorker("serviceTaskJob");
    setupAutocompleteWorker("scriptTaskJob");

    // process with two different task types
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess("categoriesTest")
            .startEvent("start")
            .serviceTask("svcTask", t -> t.zeebeJobType("serviceTaskJob"))
            .scriptTask("scriptTask", t -> t.zeebeJobType("scriptTaskJob"))
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(processDefinition);

    // when: process instance runs to completion
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // wait for process to complete
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();

    // then: execution listener fired on both task types
    final var elJobs =
        RecordingExporter.jobRecords(JobIntent.COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType("tasksCategory")
            .limit(2)
            .toList();
    assertThat(elJobs)
        .hasSize(2)
        .extracting(r -> r.getValue().getElementId())
        .containsExactly("svcTask", "scriptTask");
  }

  private void setupAutocompleteWorker(final String jobType) {
    camundaClient
        .newWorker()
        .jobType(jobType)
        .handler((client, job) -> client.newCompleteCommand(job).send().join())
        .open();
  }

  private void configureGlobalExecutionListeners(final List<GlobalListenerCfg> listenerCfgs) {
    final GlobalListenersCfg globalListenersCfg = new GlobalListenersCfg();
    globalListenersCfg.setExecution(listenerCfgs);
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
      final String id,
      final String type,
      final List<String> eventTypes,
      final List<String> elementTypes,
      final boolean afterNonGlobal) {
    final GlobalListenerCfg listenerCfg = new GlobalListenerCfg();
    listenerCfg.setId(id);
    listenerCfg.setType(type);
    listenerCfg.setEventTypes(eventTypes);
    listenerCfg.setElementTypes(elementTypes);
    listenerCfg.setAfterNonGlobal(afterNonGlobal);
    return listenerCfg;
  }
}
