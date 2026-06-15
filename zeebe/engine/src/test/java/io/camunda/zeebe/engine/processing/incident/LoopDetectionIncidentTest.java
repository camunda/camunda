/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.engine.processing.incident.IncidentHelper.assertIncidentCreated;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the loop-detection mechanism that raises a {@link ErrorType#CONDITION_ERROR} incident
 * when a BPMN element is activated more times than the configured threshold within one process
 * instance.
 *
 * <p>The engine is configured with a very low threshold (3 activations) and a retry cooldown of 1
 * (re-check on every single activation) so the tests run quickly without needing hundreds of loop
 * iterations.
 */
public final class LoopDetectionIncidentTest {

  /**
   * Low threshold so tests don't need to run > 1000 loop iterations. A retry cooldown of 1 means
   * the detection fires as soon as the cumulative count exceeds {@code MAX_ACTIVATIONS}.
   */
  private static final int MAX_ACTIVATIONS = 3;

  private static final int RETRY_COOLDOWN = 1;

  private static final String PROCESS_ID = "looping-process";
  private static final String TASK_ID = "looping-task";
  private static final String GATEWAY_ID = "xor-gateway";
  private static final String JOB_TYPE = "loop-job";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  /**
   * Each test method gets its own engine so the low threshold does not interfere with any shared
   * class-level engine used by other tests in the suite.
   */
  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(
              cfg ->
                  cfg.setMaxElementActivationCount(MAX_ACTIVATIONS)
                      .setElementActivationRetryCooldown(RETRY_COOLDOWN));

  // ---------------------------------------------------------------------------
  // BPMN model helpers
  // ---------------------------------------------------------------------------

  /**
   * A process that loops indefinitely via an exclusive gateway whose default flow always returns to
   * the service task.
   *
   * <pre>
   * [Start] → [ServiceTask: looping-task]
   *               ↑                    ↓
   *               └── [XOR: default] ←─┘
   * </pre>
   */
  private static BpmnModelInstance loopingProcess() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(TASK_ID, t -> t.zeebeJobType(JOB_TYPE))
        .exclusiveGateway(GATEWAY_ID)
        .defaultFlow()
        .connectTo(TASK_ID)
        .done();
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  @Test
  public void shouldRaiseLoopDetectedIncidentWhenThresholdExceeded() {
    // given
    engine.deployment().withXmlResource(loopingProcess()).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: drive the loop by completing MAX_ACTIVATIONS jobs so the task is activated
    // (MAX_ACTIVATIONS + 1) times in total. The activation that exceeds the threshold raises the
    // incident.
    for (int i = 0; i < MAX_ACTIVATIONS; i++) {
      final long jobKey =
          RecordingExporter.jobRecords(JobIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .withType(JOB_TYPE)
              .skip(i)
              .getFirst()
              .getKey();
      engine.job().withKey(jobKey).complete();
    }

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .limit(1)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<ProcessInstanceRecordValue> taskActivation =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ID)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .skip(MAX_ACTIVATIONS)
            .getFirst();

    assertIncidentCreated(incident, taskActivation);

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.CONDITION_ERROR)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId(TASK_ID);

    // error message should mention the element id and the threshold
    assertThat(incident.getValue().getErrorMessage())
        .contains(TASK_ID)
        .contains(String.valueOf(MAX_ACTIVATIONS));
  }

  @Test
  public void shouldRaiseIncidentOnTheLoopingElementNotTheGateway() {
    // given
    engine.deployment().withXmlResource(loopingProcess()).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: the task is activated one more time than its threshold allows
    for (int i = 0; i < MAX_ACTIVATIONS; i++) {
      final long jobKey =
          RecordingExporter.jobRecords(JobIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .withType(JOB_TYPE)
              .skip(i)
              .getFirst()
              .getKey();
      engine.job().withKey(jobKey).complete();
    }
    // then: the incident is created on the repeatedly-activated SERVICE_TASK, not the gateway
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .limit(1)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incident.getValue().getElementId()).isEqualTo(TASK_ID);
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.CONDITION_ERROR);
  }

  @Test
  public void shouldNotRaiseIncidentForNonLoopingProcess() {
    // given: a straight-through process (no loop, task activated only once)
    final String processId = "non-looping-process";
    final BpmnModelInstance nonLoopingProcess =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("single-task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    engine.deployment().withXmlResource(nonLoopingProcess).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when: complete the only job
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(JOB_TYPE)
            .getFirst()
            .getKey();
    engine.job().withKey(jobKey).complete();

    // then: process completes normally — limitToProcessInstanceCompleted() short-circuits on the
    // root ELEMENT_COMPLETED event, so this assertion also proves no incident halted the process
    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .limitToProcessInstanceCompleted()
        .getLast();
  }

  @Test
  public void shouldCountActivationsPerElementNotPerProcessInstance() {
    // given: two distinct tasks each activated once (sum = 2, below per-element threshold of 3)
    final String processId = "two-element-process";
    final String taskA = "task-a";
    final String taskB = "task-b";
    final BpmnModelInstance twoTaskProcess =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(taskA, t -> t.zeebeJobType("job-a"))
            .serviceTask(taskB, t -> t.zeebeJobType("job-b"))
            .endEvent()
            .done();

    engine.deployment().withXmlResource(twoTaskProcess).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when: complete both jobs
    final long jobAKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("job-a")
            .getFirst()
            .getKey();
    engine.job().withKey(jobAKey).complete();

    final long jobBKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("job-b")
            .getFirst()
            .getKey();
    engine.job().withKey(jobBKey).complete();

    // then: process completes normally; limitToProcessInstanceCompleted() proves no incident
    // halted either element — activation counts must be per-element, not aggregated
    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .limitToProcessInstanceCompleted()
        .getLast();
  }
}
