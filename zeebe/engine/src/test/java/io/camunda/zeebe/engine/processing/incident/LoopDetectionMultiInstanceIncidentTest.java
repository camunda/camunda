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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
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
 * <p>Parallel MI spawns children until the activation threshold is exceeded; the incident is raised
 * on the child activation that crosses it and the remaining children are not spawned. When the body
 * is re-activated in a loop and the cumulative child count crosses the threshold, the incident fires
 * on the child activation that crosses it — the same way sequential MI counts its children
 * one-by-one.
 */
public final class LoopDetectionMultiInstanceIncidentTest {

  private static final int MAX_ACTIVATIONS = 4;
  private static final int RETRY_COOLDOWN = 3;

  /**
   * Parallel MI: a collection strictly larger than {@code MAX_ACTIVATIONS} is blocked wholesale by
   * the batch guard on the body.
   */
  private static final int LARGE_COLLECTION_SIZE = MAX_ACTIVATIONS + 1; // = 5

  /**
   * Parallel MI loop: a collection that fits within {@code MAX_ACTIVATIONS} (so the batch guard
   * does not block) that is re-activated in a loop until the cumulative child count crosses the
   * threshold.
   */
  private static final int LOOP_COLLECTION_SIZE = 3;

  private static final String PARALLEL_PROCESS_ID = "parallel-mi-process";
  private static final String PARALLEL_LOOP_PROCESS_ID = "parallel-mi-loop-process";
  private static final String SEQUENTIAL_PROCESS_ID = "sequential-mi-loop-process";
  private static final String TASK_ID = "mi-task";
  private static final String GATEWAY_ID = "loop-gateway";
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

  /**
   * Looping process with a parallel MI: start → parallel MI service task → exclusive gateway that
   * loops back to the MI body. Each loop iteration re-activates the parallel MI and spawns a fresh
   * batch of children, so the shared child-activation counter accumulates across iterations.
   */
  private static BpmnModelInstance parallelMiLoopingProcess() {
    return Bpmn.createExecutableProcess(PARALLEL_LOOP_PROCESS_ID)
        .startEvent()
        .serviceTask(
            TASK_ID,
            t ->
                t.zeebeJobType(JOB_TYPE)
                    .multiInstance(
                        mi ->
                            mi.parallel().zeebeInputCollection("=items").zeebeInputElement("item")))
        .exclusiveGateway(GATEWAY_ID)
        .defaultFlow()
        .connectTo(TASK_ID)
        .done();
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  /**
   * A <b>parallel</b> MI whose collection is larger than {@code maxActivations} is bounded by loop
   * detection: children activate up to the threshold and the incident is raised on the child that
   * crosses it (the {@code (maxActivations + 1)}th) — not on the multi-instance body — and the
   * batch stops there instead of materialising the whole collection. This is intentional and
   * configurable (raise {@code maxElementActivationCount} or set a per-type override for genuinely
   * large collections), not a false positive.
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

    // then: incident fires on the child activation that crosses the threshold (the (MAX + 1)th
    // SERVICE_TASK activation), not on the multi-instance body
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

  /**
   * A <b>parallel</b> MI whose collection fits within {@code maxActivations} (so the batch guard
   * does not block) but which is re-activated in a loop raises the incident on the <b>child</b>
   * activation that pushes the cumulative child count past the threshold — not on the body.
   *
   * <h2>Numeric example ({@code MAX=4, COLLECTION=3})</h2>
   *
   * <pre>
   * Iteration 1: children activate at cumulative counts 1, 2, 3 (all &le; MAX)
   * Iteration 2: child activates at count 4 (equals MAX), then
   *              child activates at count 5 &gt; MAX=4 ← incident fires on this child
   * </pre>
   */
  @Test
  public void shouldRaiseIncidentOnChildWhenParallelMultiInstanceLoopExceedsMaxActivations() {
    // given: a parallel MI whose collection (3) fits within the threshold (4), inside a loop
    final List<Integer> items =
        IntStream.rangeClosed(1, LOOP_COLLECTION_SIZE).boxed().collect(Collectors.toList());
    engine.deployment().withXmlResource(parallelMiLoopingProcess()).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PARALLEL_LOOP_PROCESS_ID)
            .withVariable("items", items)
            .create();

    // when: the first iteration's children are completed, so the body loops and re-activates its
    // children, pushing the cumulative child count past the threshold in the second iteration
    for (int i = 0; i < LOOP_COLLECTION_SIZE; i++) {
      final long jobKey =
          RecordingExporter.jobRecords(JobIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .withType(JOB_TYPE)
              .skip(i)
              .getFirst()
              .getKey();
      engine.job().withKey(jobKey).complete();
    }

    // then: the incident fires on the child activation that crosses the threshold (the 5th child
    // activation overall), not on the multi-instance body
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var childActivatingRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ID)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .skip(MAX_ACTIVATIONS)
            .getFirst();

    assertIncidentCreated(incident, childActivatingRecord);

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.CONDITION_ERROR)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId(TASK_ID);

    assertThat(incident.getValue().getErrorMessage())
        .contains(TASK_ID)
        .contains(String.valueOf(MAX_ACTIVATIONS));
  }

  /**
   * A parallel MI with a collection far larger than the threshold activates children only up to the
   * threshold and then stops the batch at the crossing child, instead of activating every child and
   * flooding the instance with one incident per child.
   */
  @Test
  public void shouldStopActivatingChildrenOnceThresholdIsCrossed() {
    // given: a parallel MI whose collection is far larger than the threshold
    final int largeCollectionSize = 50;
    final List<Integer> items =
        IntStream.rangeClosed(1, largeCollectionSize).boxed().collect(Collectors.toList());
    engine.deployment().withXmlResource(parallelMiProcess()).deploy();

    // when: the instance is created, the batch activates children up to the threshold and stops
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PARALLEL_PROCESS_ID)
            .withVariable("items", items)
            .create();

    // then: the batch finishes early (a single ACTIVATED event) once the threshold is crossed
    RecordingExporter.processInstanceBatchRecords()
        .withProcessInstanceKey(processInstanceKey)
        .limit(r -> r.getIntent() == ProcessInstanceBatchIntent.ACTIVATED)
        .toList();

    // only MAX_ACTIVATIONS + 1 children were activated (the threshold plus the crossing child that
    // carries the incident) — far fewer than the collection size
    final long childActivations =
        RecordingExporter.getRecords().stream()
            .filter(r -> r.getValueType() == ValueType.PROCESS_INSTANCE)
            .filter(r -> r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .map(Record::getValue)
            .map(ProcessInstanceRecordValue.class::cast)
            .filter(v -> TASK_ID.equals(v.getElementId()))
            .filter(v -> v.getBpmnElementType() == BpmnElementType.SERVICE_TASK)
            .count();
    assertThat(childActivations).isEqualTo(MAX_ACTIVATIONS + 1);
  }

  /**
   * The child that crosses the threshold holds a loop-detection incident; resolving it must let
   * that child proceed (create its job) instead of re-raising the same incident, so the process can
   * be recovered.
   */
  @Test
  public void shouldRecoverChildWhenResolvingItsLoopDetectionIncident() {
    // given: a parallel MI over the threshold activates children up to the threshold, and the
    // crossing child raises its own loop-detection incident
    final List<Integer> items =
        IntStream.rangeClosed(1, LARGE_COLLECTION_SIZE).boxed().collect(Collectors.toList());
    engine.deployment().withXmlResource(parallelMiProcess()).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PARALLEL_PROCESS_ID)
            .withVariable("items", items)
            .create();

    // the crossing child raises the loop-detection incident
    final Record<IncidentRecordValue> childIncident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when: the child's loop-detection incident is resolved
    engine.incident().ofInstance(processInstanceKey).withKey(childIncident.getKey()).resolve();

    // then: the child recovers and creates its job, so every activated child now has a job
    final var jobs =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(JOB_TYPE)
            .limit(LARGE_COLLECTION_SIZE)
            .collect(Collectors.toList());
    assertThat(jobs).hasSize(LARGE_COLLECTION_SIZE);
  }

  /**
   * After the batch is throttled and its crossing-child incident is resolved, the parallel MI
   * drains the remaining collection items one at a time (through the sequential path), so the
   * instance is not stuck with the collection half-processed and eventually completes.
   */
  @Test
  public void shouldDrainRemainingParallelMiChildrenAndCompleteAfterResolving() {
    // given: collection larger than the batch bound, so the tail item(s) must be drained after the
    // batch (MAX_ACTIVATIONS + 1 children) is activated
    final int collectionSize = MAX_ACTIVATIONS + 2;
    final List<Integer> items =
        IntStream.rangeClosed(1, collectionSize).boxed().collect(Collectors.toList());
    engine.deployment().withXmlResource(parallelMiProcess()).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PARALLEL_PROCESS_ID)
            .withVariable("items", items)
            .create();

    // resolve the crossing child's incident so it can run and, once finished, drain the tail item
    final Record<IncidentRecordValue> childIncident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    engine.incident().ofInstance(processInstanceKey).withKey(childIncident.getKey()).resolve();

    // complete every child job; the tail item is activated once the batch children have finished
    for (int i = 0; i < collectionSize; i++) {
      final long jobKey =
          RecordingExporter.jobRecords(JobIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .withType(JOB_TYPE)
              .skip(i)
              .getFirst()
              .getKey();
      engine.job().withKey(jobKey).complete();
    }

    // then: the instance is not stuck — it completes with every collection item processed
    final Record<ProcessInstanceRecordValue> completed =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    assertThat(completed).isNotNull();
  }
}
