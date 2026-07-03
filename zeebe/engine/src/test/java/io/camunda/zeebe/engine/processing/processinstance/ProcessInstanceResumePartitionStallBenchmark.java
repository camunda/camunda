/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Characterization benchmark for the process instance suspend/resume POC (#56552), track (a)
 * scenario 2: partition-stall.
 *
 * <p>Not part of the regular test suite — no assertions or thresholds, only measurements printed to
 * stdout for a human to read. Run explicitly:
 *
 * <pre>
 * ./mvnw verify -pl zeebe/engine \
 *   -Dtest=ProcessInstanceResumePartitionStallBenchmark -DskipTests=false -DskipITs -Dquickly
 * </pre>
 *
 * <p>{@link ProcessInstanceResumeProcessor} drains its buffer atomically inside a single processing
 * cycle/transaction on the partition's one stream-processor thread — nothing else on that partition
 * can make progress until the RESUME command's own transaction commits (draining the buffer only
 * re-appends the buffered commands as follow-ups for *future* cycles; the drained commands
 * themselves aren't processed within RESUME's own cycle). This measures how long an unrelated
 * process instance's commands, queued right behind a large RESUME, have to wait for that one atomic
 * cycle to finish — baseline (nothing else happening) vs. during-drain.
 */
public final class ProcessInstanceResumePartitionStallBenchmark {

  private static final String PROCESS_ID_A = "processA";
  private static final String PROCESS_ID_B = "processB";
  private static final String JOB_TYPE = "test";

  // Just under scenario 1's observed crash boundary (~7,182 entries for this record shape) so the
  // drain is as large as possible while still succeeding — the worst case that doesn't itself
  // roll back.
  private static final int BUFFERED_COMMAND_COUNT = 7_000;
  private static final int BASELINE_SAMPLES = 30;
  private static final int DURING_DRAIN_SAMPLES = 20;

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Before
  public void setUp() {
    RecordingExporter.setMaximumWaitTime(Duration.ofMinutes(2).toMillis());
  }

  @Test
  public void measurePartitionStallDuringDrain() throws InterruptedException {
    final long instanceB = createServiceTaskProcessInstance(PROCESS_ID_B, JOB_TYPE + "-b");

    System.out.println("\n# baseline: unrelated PI variable-update latency, nothing else running");
    final long[] baseline = measureVariableUpdateLatencies(instanceB, BASELINE_SAMPLES);
    printStats("baseline", baseline);

    final long instanceA = createServiceTaskProcessInstance(PROCESS_ID_A, JOB_TYPE + "-a");
    final Record<ProcessInstanceRecordValue> taskActivatedA =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(instanceA)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();
    engine.processInstance().withInstanceKey(instanceA).suspend();
    bufferCommands(instanceA, taskActivatedA, BUFFERED_COMMAND_COUNT);

    System.out.println(
        "\n# during-drain: same measurement on B while A's RESUME("
            + BUFFERED_COMMAND_COUNT
            + ") drain runs on the same partition");
    final long[] duringDrain = new long[DURING_DRAIN_SAMPLES];
    final Thread bLoad =
        new Thread(
            () -> {
              for (int i = 0; i < DURING_DRAIN_SAMPLES; i++) {
                final long start = System.nanoTime();
                engine
                    .variables()
                    .ofScope(instanceB)
                    .withDocument(Map.of("stallProbe", i))
                    .update();
                duringDrain[i] = System.nanoTime() - start;
              }
            });
    bLoad.start();
    // fire-and-forget: the point is B's load races the drain, not that we wait for RESUMED here
    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(
                ProcessInstanceIntent.RESUME,
                new ProcessInstanceRecord().setProcessInstanceKey(instanceA))
            .key(instanceA));
    bLoad.join();
    printStats("during-drain", duringDrain);
  }

  private long[] measureVariableUpdateLatencies(final long scopeKey, final int samples) {
    final long[] latencies = new long[samples];
    for (int i = 0; i < samples; i++) {
      final long start = System.nanoTime();
      engine.variables().ofScope(scopeKey).withDocument(Map.of("baselineProbe", i)).update();
      latencies[i] = System.nanoTime() - start;
    }
    return latencies;
  }

  private void printStats(final String label, final long[] latenciesNanos) {
    final long[] sorted = latenciesNanos.clone();
    Arrays.sort(sorted);
    final long p50 = percentile(sorted, 50);
    final long p99 = percentile(sorted, 99);
    final long max = sorted[sorted.length - 1];
    System.out.printf(
        "%s: n=%d, p50=%dms, p99=%dms, max=%dms%n",
        label,
        sorted.length,
        Duration.ofNanos(p50).toMillis(),
        Duration.ofNanos(p99).toMillis(),
        Duration.ofNanos(max).toMillis());
  }

  private long percentile(final long[] sortedNanos, final int percentile) {
    final int index =
        Math.min(
            sortedNanos.length - 1, (int) Math.ceil(percentile / 100.0 * sortedNanos.length) - 1);
    return sortedNanos[Math.max(index, 0)];
  }

  private long createServiceTaskProcessInstance(final String processId, final String jobType) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    return engine.processInstance().ofBpmnProcessId(processId).create();
  }

  private void bufferCommands(
      final long processInstanceKey,
      final Record<ProcessInstanceRecordValue> taskActivated,
      final int count) {
    final ProcessInstanceRecordValue taskActivatedValue = taskActivated.getValue();
    final RecordToWrite[] commands = new RecordToWrite[count];
    for (int i = 0; i < count; i++) {
      final ProcessInstanceRecord command =
          new ProcessInstanceRecord()
              .setBpmnElementType(BpmnElementType.SERVICE_TASK)
              .setProcessInstanceKey(processInstanceKey)
              .setProcessDefinitionKey(taskActivatedValue.getProcessDefinitionKey())
              .setElementId("task")
              .setFlowScopeKey(taskActivatedValue.getFlowScopeKey())
              .setBpmnProcessId(taskActivatedValue.getBpmnProcessId())
              .setVersion(taskActivatedValue.getVersion());
      commands[i] =
          RecordToWrite.command()
              .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, command)
              .key(taskActivated.getKey());
    }
    engine.writeRecords(commands);
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMMAND_BUFFERED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(count)
        .asList();
  }
}
