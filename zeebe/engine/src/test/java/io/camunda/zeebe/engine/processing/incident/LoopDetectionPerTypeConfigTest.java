/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies per-{@link BpmnElementType} loop-detection configuration: a per-type override is applied
 * instead of the global default, and a per-type value of {@code 0} disables detection for that
 * type. {@code SERVICE_TASK} uses a threshold ({@value #SERVICE_TASK_MAX}) below the global default
 * ({@value #GLOBAL_MAX}), while {@code MULTI_INSTANCE_BODY} is disabled.
 */
public final class LoopDetectionPerTypeConfigTest {

  private static final int GLOBAL_MAX = 5;
  private static final int SERVICE_TASK_MAX = 2;

  private static final String PROCESS_ID = "looping-process";
  private static final String TASK_ID = "looping-task";
  private static final String GATEWAY_ID = "xor-gateway";
  private static final String JOB_TYPE = "loop-job";

  private static final String MI_PROCESS_ID = "parallel-mi-process";
  private static final String MI_TASK_ID = "mi-task";
  private static final String MI_JOB_TYPE = "mi-job";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(
              cfg ->
                  cfg.setMaxElementActivationCount(GLOBAL_MAX)
                      .setElementActivationRetryCooldown(1)
                      .setMaxElementActivationCountByType(
                          Map.of(
                              BpmnElementType.SERVICE_TASK,
                              SERVICE_TASK_MAX,
                              BpmnElementType.MULTI_INSTANCE_BODY,
                              0)));

  @Test
  public void shouldRaiseIncidentAtPerTypeOverrideThreshold() {
    // given: a tight loop on a service task whose per-type threshold (2) is lower than the global
    // default (5)
    engine.deployment().withXmlResource(loopingProcess()).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: the task is activated (SERVICE_TASK_MAX + 1) times by completing SERVICE_TASK_MAX jobs
    for (int i = 0; i < SERVICE_TASK_MAX; i++) {
      final long jobKey =
          RecordingExporter.jobRecords(JobIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .withType(JOB_TYPE)
              .skip(i)
              .getFirst()
              .getKey();
      engine.job().withKey(jobKey).complete();
    }

    // then: the incident is raised on the service task at its per-type threshold, not the global
    // one
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incident.getValue().getElementId()).isEqualTo(TASK_ID);
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.CONDITION_ERROR);
    assertThat(incident.getValue().getErrorMessage())
        .contains(TASK_ID)
        .contains(String.valueOf(SERVICE_TASK_MAX));
  }

  @Test
  public void shouldNotRaiseIncidentForDisabledElementType() {
    // given: a parallel MI whose body would exceed the global threshold (collection > GLOBAL_MAX),
    // but MULTI_INSTANCE_BODY is disabled via a per-type value of 0
    final int collectionSize = GLOBAL_MAX * 2;
    final List<Integer> items = IntStream.rangeClosed(1, collectionSize).boxed().toList();

    engine.deployment().withXmlResource(parallelMiProcess()).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(MI_PROCESS_ID)
            .withVariable("items", items)
            .create();

    // when: all child jobs are completed
    for (int i = 0; i < collectionSize; i++) {
      final long jobKey =
          RecordingExporter.jobRecords(JobIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .withType(MI_JOB_TYPE)
              .skip(i)
              .getFirst()
              .getKey();
      engine.job().withKey(jobKey).complete();
    }

    // then: the process completes without any loop-detection incident
    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .limitToProcessInstanceCompleted()
        .getLast();

    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .filter(r -> r.getValueType() == ValueType.INCIDENT)
                .filter(r -> r.getIntent() == IncidentIntent.CREATED)
                .toList())
        .as("loop detection disabled for MULTI_INSTANCE_BODY")
        .isEmpty();
  }

  private static BpmnModelInstance loopingProcess() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(TASK_ID, t -> t.zeebeJobType(JOB_TYPE))
        .exclusiveGateway(GATEWAY_ID)
        .defaultFlow()
        .connectTo(TASK_ID)
        .done();
  }

  private static BpmnModelInstance parallelMiProcess() {
    return Bpmn.createExecutableProcess(MI_PROCESS_ID)
        .startEvent()
        .serviceTask(
            MI_TASK_ID,
            t ->
                t.zeebeJobType(MI_JOB_TYPE)
                    .multiInstance(
                        mi ->
                            mi.parallel().zeebeInputCollection("=items").zeebeInputElement("item")))
        .endEvent()
        .done();
  }
}
