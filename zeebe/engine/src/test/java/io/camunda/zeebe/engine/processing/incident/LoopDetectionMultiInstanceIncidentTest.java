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
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that the loop-detection mechanism raises an incident when a multi-instance (MI) element
 * activates more child instances than {@code maxElementActivationCount}.
 *
 * <p>Parallel MI projects {@code collection.size()} child activations upfront, so an incident fires
 * immediately on the {@code MULTI_INSTANCE_BODY} when that exceeds the threshold. Sequential MI
 * counts its children one-by-one, so the incident fires on the child activation that pushes the
 * count beyond the threshold.
 */
public final class LoopDetectionMultiInstanceIncidentTest {

  private static final int MAX_ACTIVATIONS = 4;
  private static final int RETRY_COOLDOWN = 3;

  /**
   * Parallel MI: collection strictly larger than {@code MAX_ACTIVATIONS} triggers the upfront
   * projection check on the body.
   */
  private static final int LARGE_COLLECTION_SIZE = MAX_ACTIVATIONS + 1; // = 5

  private static final String PARALLEL_PROCESS_ID = "parallel-mi-process";
  private static final String SEQUENTIAL_PROCESS_ID = "sequential-mi-loop-process";
  private static final String TASK_ID = "mi-task";
  private static final String JOB_TYPE = "mi-job";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

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

  /** Straight process: start → parallel MI service task → end. No outer loop. */
  private static BpmnModelInstance parallelMiProcess() {
    return Bpmn.createExecutableProcess(PARALLEL_PROCESS_ID)
        .startEvent()
        .serviceTask(
            TASK_ID,
            t ->
                t.zeebeJobType(JOB_TYPE)
                    .multiInstance(
                        mi ->
                            mi.parallel().zeebeInputCollection("=items").zeebeInputElement("item")))
        .endEvent()
        .done();
  }

  /**
   * Straight process with sequential MI: start → sequential MI service task → end.
   *
   * <p>Sequential MI children activations are counted individually. When the collection size
   * exceeds {@code maxActivations}, the incident fires on the child activation that would exceed
   * the threshold.
   */
  private static BpmnModelInstance sequentialMiLoopingProcess() {
    return Bpmn.createExecutableProcess(SEQUENTIAL_PROCESS_ID)
        .startEvent()
        .serviceTask(
            TASK_ID,
            t ->
                t.zeebeJobType(JOB_TYPE)
                    .multiInstance(
                        mi ->
                            mi.sequential()
                                .zeebeInputCollection("=items")
                                .zeebeInputElement("item")))
        .endEvent()
        .done();
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  /**
   * A <b>parallel</b> MI with a collection larger than {@code maxActivations} raises a
   * loop-detection incident immediately on the {@code MULTI_INSTANCE_BODY} activation.
   *
   * <p>The engine projects {@code collection.size()} child activations and compares against the
   * threshold before any child is activated. No jobs are created.
   */
  @Test
  public void shouldRaiseIncidentForParallelMultiInstanceWhenCollectionExceedsMaxActivations() {
    // given
    final List<Integer> items =
        IntStream.rangeClosed(1, LARGE_COLLECTION_SIZE).boxed().collect(Collectors.toList());
    engine.deployment().withXmlResource(parallelMiProcess()).deploy();

    // when: a process instance is created with a collection larger than maxActivations
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PARALLEL_PROCESS_ID)
            .withVariable("items", items)
            .create();

    // then: incident fires on the MULTI_INSTANCE_BODY activation — no children are activated,
    // no jobs are created.
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var activatingRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ID)
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .getFirst();

    assertIncidentCreated(incident, activatingRecord);

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.CONDITION_ERROR)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId(TASK_ID);

    assertThat(incident.getValue().getErrorMessage())
        .contains(TASK_ID)
        .contains(String.valueOf(MAX_ACTIVATIONS));
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  /**
   * Baseline: a single non-looping parallel MI with a collection smaller than {@code
   * maxActivations} completes without any incident (body counter = 1 after one run).
   */
  @Test
  public void shouldNotRaiseIncidentBeforeThresholdIsReached() {
    // given
    final List<Integer> items = IntStream.rangeClosed(1, MAX_ACTIVATIONS).boxed().toList();

    engine.deployment().withXmlResource(parallelMiProcess()).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PARALLEL_PROCESS_ID)
            .withVariable("items", items)
            .create();
    // when: complete all jobs (one per item, total = MAX_ACTIVATIONS)
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

    // then: process completes successfully, no incident is raised
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
        .as("no incident before threshold is reached")
        .isEmpty();
  }

  /**
   * A <b>sequential</b> MI with a collection larger than {@code maxActivations} raises a
   * loop-detection incident when activating a child would exceed the threshold.
   *
   * <p>Sequential MI children are counted individually — each child activation increments the
   * counter by 1. When the collection contains more items than {@code maxActivations}, the incident
   * fires on the child that would cause the counter to exceed the threshold.
   *
   * <h2>Numeric example ({@code MAX=4, COLLECTION=5})</h2>
   *
   * <pre>
   * Child 1 activation: count=1
   * Child 2 activation: count=2
   * Child 3 activation: count=3
   * Child 4 activation: count=4 (equals MAX)
   * Child 5 activation: would make count=5 &gt; MAX=4 ← incident fires
   * </pre>
   */
  @Test
  public void shouldRaiseIncidentForSequentialMultiInstanceWhenCollectionExceedsMaxActivations() {
    // given
    final List<Integer> items = IntStream.rangeClosed(1, LARGE_COLLECTION_SIZE).boxed().toList();

    engine.deployment().withXmlResource(sequentialMiLoopingProcess()).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(SEQUENTIAL_PROCESS_ID)
            .withVariable("items", items)
            .create();

    // when: complete MAX_ACTIVATIONS sequential child job instances.
    // Sequential MI creates and processes children one at a time.
    // The incident fires when attempting to activate the (MAX_ACTIVATIONS + 1)th child,
    // which would exceed the activation threshold.
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

    // then: incident fires when attempting to activate the next sequential child,
    // which would exceed the activation threshold.
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var activatingRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ID)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .skip(MAX_ACTIVATIONS)
            .getFirst();

    assertIncidentCreated(incident, activatingRecord);

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.CONDITION_ERROR)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId(TASK_ID);

    assertThat(incident.getValue().getErrorMessage())
        .contains(TASK_ID)
        .contains(String.valueOf(MAX_ACTIVATIONS));
  }
}
