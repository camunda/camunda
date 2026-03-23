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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Integration tests verifying that global execution listeners produce listener jobs when process
 * instances execute, and that the merge ordering with BPMN-level execution listeners is correct.
 */
public class GlobalExecutionListenerTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

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
}
