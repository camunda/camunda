/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Integration tests verifying that global execution listeners produce listener jobs when process
 * instances execute, and that the merge ordering with BPMN-level execution listeners is correct.
 *
 * <p>Uses {@code @Rule} (not {@code @ClassRule}) so each test gets a fresh engine — global listener
 * registrations do not accumulate across tests.
 */
public class GlobalExecutionListenerTest {

  @Rule public final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "gel-test-process";
  private static final String GLOBAL_EL_TYPE = "global_el_job";
  private static final String BPMN_EL_TYPE = "bpmn_el_job";
  private static final String SERVICE_TASK_TYPE = "service_task_job";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  // ---------------------------------------------------------------------------
  // Basic job creation
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnServiceTaskStart() {
    // given
    ENGINE
        .globalListener()
        .withId("el-svc-start")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("serviceTask")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — global EL start job is created before the service task job
    final var elJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(elJob.getValue().getElementId()).isEqualTo("task");

    // complete the listener job so the service task can proceed
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    // verify the process completes
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCreateGlobalELJobOnServiceTaskEnd() {
    // given
    ENGINE
        .globalListener()
        .withId("el-svc-end")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("end")
        .withElementTypes("serviceTask")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — complete the service task
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    // then — global EL end job is created
    final var elJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(elJob.getValue().getElementId()).isEqualTo("task");

    // complete the listener job
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCreateGlobalELJobOnProcessStart() {
    // given
    ENGINE
        .globalListener()
        .withId("el-process-start")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("process")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().manualTask().endEvent().done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — global EL start job fires on the process element
    final var elJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(elJob.getValue().getElementId()).isEqualTo(PROCESS_ID);

    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCreateGlobalELJobOnProcessEnd() {
    // given
    ENGINE
        .globalListener()
        .withId("el-process-end")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("end")
        .withElementTypes("process")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().manualTask().endEvent().done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — wait for the process to reach completion
    // then — global EL end job is created on the process element
    final var elJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(elJob.getValue().getElementId()).isEqualTo(PROCESS_ID);

    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Filtering — non-matching element/event types
  // ---------------------------------------------------------------------------

  @Test
  public void shouldNotCreateGlobalELJobForNonMatchingElementType() {
    // given — listener scoped to userTask only
    ENGINE
        .globalListener()
        .withId("el-user-only")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("userTask")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — complete the service task (no listener should fire)
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    // then — process completes without any global EL jobs
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();

    assertThat(
            jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(piKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .limit(1)
                .count())
        .isZero();
  }

  @Test
  public void shouldNotCreateGlobalELJobForNonMatchingEventType() {
    // given — listener scoped to end events on serviceTask
    ENGINE
        .globalListener()
        .withId("el-svc-end-only")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("end")
        .withElementTypes("serviceTask")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — before completing the service task, verify no start EL job was created
    // The service task should be activated directly (no EL start job blocking it)
    final var serviceTaskCreated =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withType(SERVICE_TASK_TYPE)
            .getFirst();
    assertThat(serviceTaskCreated).isNotNull();

    // The global EL is for "end" only, so no "start" EL job should exist yet
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    // then — now an end EL job should be created
    final var endElJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(endElJob.getValue().getElementId()).isEqualTo("task");
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Categories matching
  // ---------------------------------------------------------------------------

  @Test
  public void shouldUseCategoriesForMatching() {
    // given — listener with "tasks" category for start events
    ENGINE
        .globalListener()
        .withId("el-tasks-cat")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withCategories("tasks")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — the "tasks" category should match serviceTask
    final var elJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(elJob.getValue().getElementId()).isEqualTo("task");

    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Ordering: global before BPMN-level listeners
  // ---------------------------------------------------------------------------

  @Test
  public void shouldRunGlobalListenersBeforeBpmnListenersByDefault() {
    // given
    ENGINE
        .globalListener()
        .withId("el-before")
        .withType("global_start_el")
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("serviceTask")
        .create();

    // process with a BPMN-level start execution listener on the service task
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType(SERVICE_TASK_TYPE).zeebeStartExecutionListener(BPMN_EL_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — complete the global EL job first, then the BPMN EL job
    ENGINE.job().ofInstance(piKey).withType("global_start_el").complete();
    ENGINE.job().ofInstance(piKey).withType(BPMN_EL_TYPE).complete();
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    // then — verify ordering: global EL completed before BPMN EL
    assertThat(
            jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(piKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withElementId("task")
                .limit(2))
        .extracting(r -> r.getValue().getType())
        .containsExactly("global_start_el", BPMN_EL_TYPE);
  }

  @Test
  public void shouldRunGlobalListenersAfterBpmnListenersWhenAfterNonGlobal() {
    // given
    ENGINE
        .globalListener()
        .withId("el-after")
        .withType("global_start_el")
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("serviceTask")
        .afterNonGlobal()
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType(SERVICE_TASK_TYPE).zeebeStartExecutionListener(BPMN_EL_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — BPMN EL runs first, then global EL
    ENGINE.job().ofInstance(piKey).withType(BPMN_EL_TYPE).complete();
    ENGINE.job().ofInstance(piKey).withType("global_start_el").complete();
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    // then — verify ordering: BPMN EL completed before global EL
    assertThat(
            jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(piKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withElementId("task")
                .limit(2))
        .extracting(r -> r.getValue().getType())
        .containsExactly(BPMN_EL_TYPE, "global_start_el");
  }

  // ---------------------------------------------------------------------------
  // Priority ordering among global listeners
  // ---------------------------------------------------------------------------

  @Test
  public void shouldOrderGlobalListenersByPriority() {
    // given — two global listeners with different priorities
    ENGINE
        .globalListener()
        .withId("el-low-prio")
        .withType("low_prio_el")
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("serviceTask")
        .withPriority(10)
        .create();

    ENGINE
        .globalListener()
        .withId("el-high-prio")
        .withType("high_prio_el")
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("serviceTask")
        .withPriority(100)
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — complete listener jobs in priority order (higher first)
    ENGINE.job().ofInstance(piKey).withType("high_prio_el").complete();
    ENGINE.job().ofInstance(piKey).withType("low_prio_el").complete();
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    // then — higher priority listener runs first
    assertThat(
            jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(piKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withElementId("task")
                .limit(2))
        .extracting(r -> r.getValue().getType())
        .containsExactly("high_prio_el", "low_prio_el");
  }

  // ---------------------------------------------------------------------------
  // Variable setting from global EL jobs
  // ---------------------------------------------------------------------------

  @Test
  public void shouldAllowVariableSettingFromGlobalELJob() {
    // given
    ENGINE
        .globalListener()
        .withId("el-var-set")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("serviceTask")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — complete the global EL with a variable
    ENGINE
        .job()
        .ofInstance(piKey)
        .withType(GLOBAL_EL_TYPE)
        .withVariables(Map.of("injected", "from-global-el"))
        .complete();

    // then — the service task job should see the variable
    final var serviceJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withType(SERVICE_TASK_TYPE)
            .getFirst();
    assertThat(serviceJob.getValue().getVariables()).containsEntry("injected", "from-global-el");

    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Multiple event types per listener
  // ---------------------------------------------------------------------------

  @Test
  public void shouldSupportMultipleEventTypesPerListener() {
    // given — a single listener handling both start and end events on serviceTask
    ENGINE
        .globalListener()
        .withId("el-multi-event")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start", "end")
        .withElementTypes("serviceTask")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — complete the start EL, the service task, then the end EL
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // start EL
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // end EL

    // then — two EL jobs were completed (start and end)
    assertThat(
            jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(piKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withElementId("task")
                .limit(2))
        .hasSize(2);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Empty scope matches all element types
  // ---------------------------------------------------------------------------

  @Test
  public void shouldMatchAllElementTypesWhenNoScopeConfigured() {
    // given — listener with no elementTypes or categories (matches all element types)
    ENGINE
        .globalListener()
        .withId("el-all-scope")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — EL job fires on the process start AND the service task start
    final var elJobs =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .limit(2)
            .toList();

    assertThat(elJobs).hasSize(2);
    assertThat(elJobs.get(0).getValue().getElementId()).isEqualTo(PROCESS_ID);
    assertThat(elJobs.get(1).getValue().getElementId()).isEqualTo("task");

    // complete all jobs so the process finishes
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // process start EL
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // task start EL
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Combined categories + elementTypes (union)
  // ---------------------------------------------------------------------------

  @Test
  public void shouldMatchUnionOfCategoriesAndElementTypes() {
    // given — listener combining categories (gateways) with explicit elementTypes (serviceTask)
    ENGINE
        .globalListener()
        .withId("el-union")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withCategories("gateways")
        .withElementTypes("serviceTask")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .exclusiveGateway("gw")
            .sequenceFlowId("to-task")
            .defaultFlow()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — EL fires on both the exclusive gateway (from gateways category) and the service task
    final var elJobs =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .limit(2)
            .toList();

    assertThat(elJobs).hasSize(2);
    assertThat(elJobs.get(0).getValue().getElementId()).isEqualTo("gw");
    assertThat(elJobs.get(1).getValue().getElementId()).isEqualTo("task");

    // complete all jobs
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // gw start EL
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // task start EL
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Gateway element type — start supported, end not supported
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnGatewayStartButNotEnd() {
    // given — two listeners: one for start, one for end, both on exclusiveGateway
    ENGINE
        .globalListener()
        .withId("el-gw-start")
        .withType(GLOBAL_EL_TYPE + "_start")
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("exclusiveGateway")
        .create();

    ENGINE
        .globalListener()
        .withId("el-gw-end")
        .withType(GLOBAL_EL_TYPE + "_end")
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("end")
        .withElementTypes("exclusiveGateway")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .exclusiveGateway("gw")
            .sequenceFlowId("to-end")
            .defaultFlow()
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — start EL fires on the gateway
    final var startElJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE + "_start")
            .getFirst();
    assertThat(startElJob.getValue().getElementId()).isEqualTo("gw");

    // complete the start EL job
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE + "_start").complete();

    // wait for the process to complete (no service task to block on)
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();

    // then — end EL should NOT have fired (gateways don't support end events)
    assertThat(
            jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(piKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withType(GLOBAL_EL_TYPE + "_end")
                .exists())
        .isFalse();
  }

  // ---------------------------------------------------------------------------
  // Incident on failed global execution listener job
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateIncidentWhenGlobalELJobFails() {
    // given
    ENGINE
        .globalListener()
        .withId("el-incident")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("serviceTask")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — fail the global EL job with no retries remaining
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).withRetries(0).fail();

    // then — incident is created
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(piKey)
        .hasErrorType(ErrorType.EXECUTION_LISTENER_NO_RETRIES)
        .hasErrorMessage("No more retries left.");

    // when — resolve the incident by updating retries and resolving
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).withRetries(1).updateRetries();
    ENGINE.incident().ofInstance(piKey).withKey(incident.getKey()).resolve();

    // then — EL job can now complete and the process finishes
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Additional element types: subprocess
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnSubprocessStartAndEnd() {
    // given — listener for start and end on subprocess
    ENGINE
        .globalListener()
        .withId("el-sub-start-end")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start", "end")
        .withElementTypes("subprocess")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "sub",
                sp ->
                    sp.embeddedSubProcess()
                        .startEvent("subStart")
                        .manualTask("subTask")
                        .endEvent("subEnd"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — two EL jobs (start and end) fire on the subprocess
    final var elJobs =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .limit(2)
            .toList();

    assertThat(elJobs).hasSize(2);
    assertThat(elJobs).allSatisfy(r -> assertThat(r.getValue().getElementId()).isEqualTo("sub"));

    // complete EL jobs to let the process finish
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // start EL
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // end EL

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Additional element types: call activity
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnCallActivityStart() {
    // given — listener for start on callActivity
    ENGINE
        .globalListener()
        .withId("el-call-start")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("callActivity")
        .create();

    // deploy a child process
    final BpmnModelInstance childProcess =
        Bpmn.createExecutableProcess("childProcess").startEvent().manualTask().endEvent().done();
    ENGINE.deployment().withXmlResource(childProcess).deploy();

    // deploy the parent process with a call activity
    final BpmnModelInstance parentProcess =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .callActivity("callAct", ca -> ca.zeebeProcessId("childProcess"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(parentProcess).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — global EL start job fires on the call activity
    final var elJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(elJob.getValue().getElementId()).isEqualTo("callAct");

    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Additional element types: intermediate catch event
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnIntermediateCatchEventStartAndEnd() {
    // given — listener for start and end on intermediateCatchEvent
    ENGINE
        .globalListener()
        .withId("el-ice")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start", "end")
        .withElementTypes("intermediateCatchEvent")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent("timer", ic -> ic.timerWithDuration("PT0S"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — start and end EL jobs are created for the intermediate catch event
    final var elJobs =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .limit(2)
            .toList();

    assertThat(elJobs).hasSize(2);
    assertThat(elJobs).allSatisfy(r -> assertThat(r.getValue().getElementId()).isEqualTo("timer"));

    // complete EL jobs
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // start
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // end

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Additional element types: start event (supports end only, not start)
  // ---------------------------------------------------------------------------

  @Test
  public void shouldNotCreateGlobalELJobForStartOnStartEvent() {
    // given — listener for "start" on startEvent (not supported per the event matrix)
    ENGINE
        .globalListener()
        .withId("el-se-start")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("startEvent")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("myStart")
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — complete the service task so the process finishes
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    // then — no EL job should fire because startEvent does not support "start"
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();

    assertThat(
            jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(piKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withType(GLOBAL_EL_TYPE)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldCreateGlobalELJobOnStartEventEnd() {
    // given — listener for "end" on startEvent (supported per the event matrix)
    ENGINE
        .globalListener()
        .withId("el-se-end")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("end")
        .withElementTypes("startEvent")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("myStart")
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — end EL fires on startEvent
    final var elJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(elJob.getValue().getElementId()).isEqualTo("myStart");

    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Additional element types: end event (supports start only, not end)
  // ---------------------------------------------------------------------------

  @Test
  public void shouldNotCreateGlobalELJobForEndOnEndEvent() {
    // given — listener for "end" on endEvent (not supported per the event matrix)
    ENGINE
        .globalListener()
        .withId("el-ee-end")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("end")
        .withElementTypes("endEvent")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().manualTask().endEvent("myEnd").done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — process completes without EL jobs (endEvent does not support "end")
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();

    assertThat(
            jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(piKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withType(GLOBAL_EL_TYPE)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldCreateGlobalELJobOnEndEventStart() {
    // given — listener for "start" on endEvent (supported per the event matrix)
    ENGINE
        .globalListener()
        .withId("el-ee-start")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("endEvent")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().manualTask().endEvent("myEnd").done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — start EL fires on endEvent
    final var elJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(elJob.getValue().getElementId()).isEqualTo("myEnd");

    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Multiple matching listeners on the same element
  // ---------------------------------------------------------------------------

  @Test
  public void shouldFireMultipleMatchingListenersOnSameElement() {
    // given — two global listeners both matching serviceTask start
    ENGINE
        .globalListener()
        .withId("el-multi-a")
        .withType("multi_el_a")
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("serviceTask")
        .withPriority(100)
        .create();

    ENGINE
        .globalListener()
        .withId("el-multi-b")
        .withType("multi_el_b")
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("serviceTask")
        .withPriority(50)
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — complete both EL jobs and the service task
    ENGINE.job().ofInstance(piKey).withType("multi_el_a").complete();
    ENGINE.job().ofInstance(piKey).withType("multi_el_b").complete();
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    // then — both EL jobs were created and completed in priority order
    assertThat(
            jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(piKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withElementId("task")
                .limit(2))
        .extracting(r -> r.getValue().getType())
        .containsExactly("multi_el_a", "multi_el_b");

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Parallel gateway (start only, no end)
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnParallelGatewayStartButNotEnd() {
    // given — listeners for both start and end on parallelGateway
    ENGINE
        .globalListener()
        .withId("el-pg-start")
        .withType(GLOBAL_EL_TYPE + "_start")
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("parallelGateway")
        .create();

    ENGINE
        .globalListener()
        .withId("el-pg-end")
        .withType(GLOBAL_EL_TYPE + "_end")
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("end")
        .withElementTypes("parallelGateway")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .manualTask("taskA")
            .parallelGateway("join")
            .endEvent()
            .moveToNode("fork")
            .manualTask("taskB")
            .connectTo("join")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — start EL fires on both fork and join gateways
    final var startElJobs =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE + "_start")
            .limit(2)
            .toList();
    assertThat(startElJobs).hasSize(2);
    assertThat(startElJobs)
        .extracting(r -> r.getValue().getElementId())
        .containsExactlyInAnyOrder("fork", "join");

    // complete start EL jobs
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE + "_start").complete();
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE + "_start").complete();

    // wait for process completion
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();

    // then — end EL should NOT have fired (parallel gateways don't support end events)
    assertThat(
            jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(piKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withType(GLOBAL_EL_TYPE + "_end")
                .exists())
        .isFalse();
  }

  // ---------------------------------------------------------------------------
  // Boundary event (supports end only, not start)
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnBoundaryEventEnd() {
    // given — listener for "end" on boundaryEvent
    ENGINE
        .globalListener()
        .withId("el-be-end")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("end")
        .withElementTypes("boundaryEvent")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .boundaryEvent("boundary", b -> b.timerWithDuration("PT0S"))
            .endEvent("boundaryEnd")
            .moveToActivity("task")
            .endEvent("normalEnd")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — end EL fires on the boundary event
    final var elJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(elJob.getValue().getElementId()).isEqualTo("boundary");

    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldNotCreateGlobalELJobForStartOnBoundaryEvent() {
    // given — listener for "start" on boundaryEvent (not supported)
    ENGINE
        .globalListener()
        .withId("el-be-start")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("boundaryEvent")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .boundaryEvent("boundary", b -> b.timerWithDuration("PT0S"))
            .endEvent("boundaryEnd")
            .moveToActivity("task")
            .endEvent("normalEnd")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — wait for process to complete (boundary timer fires immediately)
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();

    // then — no start EL should have fired on the boundary event
    assertThat(
            jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(piKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withType(GLOBAL_EL_TYPE)
                .exists())
        .isFalse();
  }

  // ---------------------------------------------------------------------------
  // Version pinning: config change mid-execution does not affect in-flight
  // ---------------------------------------------------------------------------

  @Test
  public void shouldUsePinnedConfigForInFlightInstance() {
    // given — register a global EL for start + end on serviceTask
    ENGINE
        .globalListener()
        .withId("el-pin-test")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start", "end")
        .withElementTypes("serviceTask")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when — start an instance and complete the start EL job
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // start EL

    // now update the global listener config to use a DIFFERENT job type
    final String updatedType = "updated_el_type";
    ENGINE
        .globalListener()
        .withId("el-pin-test")
        .withType(updatedType)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start", "end")
        .withElementTypes("serviceTask")
        .update();

    // complete the service task — this triggers the end EL
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    // then — the end EL job should still use the ORIGINAL type (pinned config)
    final var endElJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .filter(r -> r.getValue().getElementId().equals("task"))
            .skip(1) // skip the start EL — get the end EL
            .getFirst();
    assertThat(endElJob.getValue().getType()).isEqualTo(GLOBAL_EL_TYPE);

    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // end EL

    // verify process completes
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();

    // verify that a NEW instance uses the updated config
    final long piKey2 = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var newStartElJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey2)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(updatedType)
            .getFirst();
    assertThat(newStartElJob.getValue().getElementId()).isEqualTo("task");
  }

  // ---------------------------------------------------------------------------
  // Explicit categories: [all] matches all element types
  // ---------------------------------------------------------------------------

  @Test
  public void shouldMatchAllElementTypesWithExplicitAllCategory() {
    // given — listener with categories: [all] (explicit "all" category)
    ENGINE
        .globalListener()
        .withId("el-all-cat")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withCategories("all")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — EL fires on both process start and service task start
    final var elJobs =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .limit(2)
            .toList();

    assertThat(elJobs).hasSize(2);
    assertThat(elJobs.get(0).getValue().getElementId()).isEqualTo(PROCESS_ID);
    assertThat(elJobs.get(1).getValue().getElementId()).isEqualTo("task");

    // complete jobs
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // process start
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // task start
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Events category
  // ---------------------------------------------------------------------------

  @Test
  public void shouldMatchEventElementsWithEventsCategory() {
    // given — listener for "end" events on "events" category (startEvent, endEvent, etc.)
    // startEvent supports "end" per the compatibility matrix
    ENGINE
        .globalListener()
        .withId("el-events-cat")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("end")
        .withCategories("events")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("myStart")
            .manualTask()
            .endEvent("myEnd")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — startEvent supports "end", so an EL fires on the start event's end
    final var elJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(elJob.getValue().getElementId()).isEqualTo("myStart");

    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Additional element types: event subprocess
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnEventSubprocessStartAndEnd() {
    // given — listener for start + end on eventSubprocess
    ENGINE
        .globalListener()
        .withId("el-evtsub")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start", "end")
        .withElementTypes("eventSubprocess")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "evtSub",
                sp -> sp.startEvent("evtSubStart").timerWithDuration("PT0S").endEvent("evtSubEnd"))
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — two EL jobs (start and end) fire on the event subprocess
    final var elJobs =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .limit(2)
            .toList();

    assertThat(elJobs).hasSize(2);
    assertThat(elJobs).allSatisfy(r -> assertThat(r.getValue().getElementId()).isEqualTo("evtSub"));

    // complete EL jobs
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // start
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // end

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Additional element types: intermediate throw event
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnIntermediateThrowEventStartAndEnd() {
    // given — listener for start + end on intermediateThrowEvent
    ENGINE
        .globalListener()
        .withId("el-throw")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start", "end")
        .withElementTypes("intermediateThrowEvent")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateThrowEvent("throwEvt")
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — two EL jobs (start and end) fire on the intermediate throw event
    final var elJobs =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .limit(2)
            .toList();

    assertThat(elJobs).hasSize(2);
    assertThat(elJobs)
        .allSatisfy(r -> assertThat(r.getValue().getElementId()).isEqualTo("throwEvt"));

    // complete EL jobs
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // start
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // end

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Additional element types: multi-instance body
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnMultiInstanceBody() {
    // given — listener for start + end on multiInstanceBody
    ENGINE
        .globalListener()
        .withId("el-mi-body")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start", "end")
        .withElementTypes("multiInstanceBody")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("miTask", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .multiInstance(m -> m.parallel().zeebeInputCollectionExpression("[1,2]"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — start EL fires on the multi-instance body
    final var startElJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(startElJob.getValue().getElementId()).isEqualTo("miTask");

    // complete start EL, then complete both service task iterations
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // MI body start EL
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete(); // iteration 1
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete(); // iteration 2

    // then — end EL fires on the MI body
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // MI body end EL

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Additional element types: user task
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnUserTaskStartAndEnd() {
    // given — listener for start + end on userTask
    ENGINE
        .globalListener()
        .withId("el-usertask")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start", "end")
        .withElementTypes("userTask")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask("ut")
            .zeebeUserTask()
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — start EL fires on the user task
    final var startElJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(startElJob.getValue().getElementId()).isEqualTo("ut");

    // complete start EL, then complete the user task
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // start EL
    ENGINE.userTask().ofInstance(piKey).complete();

    // then — end EL fires on the user task
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // end EL

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Additional element types: script task
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnScriptTaskStartAndEnd() {
    // given — listener for start + end on scriptTask
    ENGINE
        .globalListener()
        .withId("el-script")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start", "end")
        .withElementTypes("scriptTask")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .scriptTask("scriptTask", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — start EL fires on the script task
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // start EL
    ENGINE.job().ofInstance(piKey).withType(SERVICE_TASK_TYPE).complete();
    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete(); // end EL

    assertThat(
            jobRecords(JobIntent.COMPLETED)
                .withProcessInstanceKey(piKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withElementId("scriptTask")
                .limit(2))
        .hasSize(2);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Additional element types: inclusive gateway
  // ---------------------------------------------------------------------------

  @Test
  public void shouldCreateGlobalELJobOnInclusiveGatewayStart() {
    // given — listener for start on inclusiveGateway (no end support)
    ENGINE
        .globalListener()
        .withId("el-incl-gw")
        .withType(GLOBAL_EL_TYPE)
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("inclusiveGateway")
        .create();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .inclusiveGateway("inclGw")
            .defaultFlow()
            .manualTask()
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long piKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — start EL fires on the inclusive gateway
    final var elJob =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withType(GLOBAL_EL_TYPE)
            .getFirst();
    assertThat(elJob.getValue().getElementId()).isEqualTo("inclGw");

    ENGINE.job().ofInstance(piKey).withType(GLOBAL_EL_TYPE).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }
}
