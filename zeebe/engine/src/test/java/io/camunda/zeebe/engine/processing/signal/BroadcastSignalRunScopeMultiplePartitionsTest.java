/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.signal;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.TagUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

/**
 * Cross-partition acceptance test for tag-scoped signal broadcast (ADR 0014). A broadcast is
 * distributed to every partition and carries the run scope on the record; this verifies the scope
 * survives that hop and that only the matching run's catch events wake, when those catch events are
 * spread across partitions.
 */
public final class BroadcastSignalRunScopeMultiplePartitionsTest {

  private static final int PARTITION_COUNT = 3;
  private static final String SIGNAL_NAME = "s";
  private static final String CATCH_ID = "catch";
  private static final String RUN_A = "runA";
  private static final String RUN_B = "runB";
  private static final String PROCESS_ID = "catch-process";

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldWakeOnlyMatchingRunAcrossPartitions() {
    // given several instances per run, spread across partitions, each waiting on the same signal
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .signal(SIGNAL_NAME)
                .endEvent()
                .done())
        .deploy();

    final List<Long> runA = createWaitingInstances(RUN_A);
    final List<Long> runB = createWaitingInstances(RUN_B);

    // sanity: the instances actually span more than one partition, otherwise this would not
    // exercise
    // the cross-partition broadcast
    final Set<Integer> partitions =
        java.util.stream.Stream.concat(runA.stream(), runB.stream())
            .map(Protocol::decodePartitionId)
            .collect(Collectors.toSet());
    assertThat(partitions).as("instances must span multiple partitions").hasSizeGreaterThan(1);

    // when a scoped broadcast is sent for run A
    engine.signal().withSignalName(SIGNAL_NAME).withRunScope(RUN_A).broadcast();

    // then every run-A catch event completes, on whichever partition it lives
    for (final long piA : runA) {
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(piA)
                  .withElementId(CATCH_ID)
                  .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                  .exists())
          .as("run-A instance %d should have woken", piA)
          .isTrue();
    }

    // and no run-B instance woke on the run-A broadcast. runA[i] and runB[i] share a partition, so
    // bounding the record stream at runA[i]'s process completion captures the window in which the
    // run-A broadcast was processed on that partition; within it, runB[i] must not have completed.
    // (Per-partition positions make this bounded existence check the correct cross-partition
    // assertion, mirroring the businessId multi-partition test.)
    for (int i = 0; i < runB.size(); i++) {
      final long piA = runA.get(i);
      final long piB = runB.get(i);
      final Predicate<Record<?>> runAProcessCompleted =
          r ->
              r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                  && r.getValue() instanceof final ProcessInstanceRecordValue v
                  && v.getProcessInstanceKey() == piA
                  && v.getBpmnElementType() == BpmnElementType.PROCESS;
      final boolean runBWoke =
          RecordingExporter.records()
              .limit(runAProcessCompleted::test)
              .processInstanceRecords()
              .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
              .withElementType(BpmnElementType.PROCESS)
              .withProcessInstanceKey(piB)
              .exists();
      assertThat(runBWoke)
          .as("run-B instance %d must not wake on a run-A broadcast", piB)
          .isFalse();
    }

    // a run-B broadcast then wakes exactly the run-B instances, across their partitions
    engine.signal().withSignalName(SIGNAL_NAME).withRunScope(RUN_B).broadcast();
    for (final long piB : runB) {
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(piB)
                  .withElementId(CATCH_ID)
                  .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                  .exists())
          .as("run-B instance %d should wake on the run-B broadcast", piB)
          .isTrue();
    }
  }

  private List<Long> createWaitingInstances(final String runScope) {
    final List<Long> keys = new ArrayList<>();
    // place one instance on each partition so the catch events (and their signal subscriptions) are
    // genuinely spread across partitions
    for (int partition = 1; partition <= PARTITION_COUNT; partition++) {
      final long key =
          engine
              .processInstance()
              .ofBpmnProcessId(PROCESS_ID)
              .onPartition(partition)
              .withTags(TagUtil.runScopeTag(runScope))
              .create();
      keys.add(key);
    }
    // wait until each catch event is active (its subscription open) before broadcasting
    for (final long key : keys) {
      RecordingExporter.processInstanceRecords()
          .withProcessInstanceKey(key)
          .withElementId(CATCH_ID)
          .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
          .getFirst();
    }
    return keys;
  }
}
