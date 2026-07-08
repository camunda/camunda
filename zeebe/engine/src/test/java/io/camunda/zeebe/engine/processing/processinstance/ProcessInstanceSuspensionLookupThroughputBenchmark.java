/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.bpmn.BpmnStreamProcessor;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Characterization benchmark for the process instance suspend/resume POC (#56552), track (b) — the
 * always-on suspension-lookup tax under real engine throughput.
 *
 * <p>Not part of the regular suite — no assertions, only stdout. Run explicitly:
 *
 * <pre>
 * ./mvnw verify -pl zeebe/engine \
 *   -Dtest=ProcessInstanceSuspensionLookupThroughputBenchmark -DskipTests=false -DskipITs -Dquickly
 * </pre>
 *
 * <p>Complements the JMH {@code SuspensionLookupBenchmark}: JMH isolates the raw lookup cost and
 * sweeps CF size to 1M; this drives the whole engine and reads its own {@code
 * zeebe.stream.processor.processing.duration} timer (mean ns/record) so the number includes the
 * lookup <em>in situ</em>, not RecordingExporter drive timing (which carries a list-growth
 * confound). Three phases on one warm engine, same workload each:
 *
 * <ol>
 *   <li><b>control</b> — {@code suspensionLookupAlwaysOn=false}: the lookup runs only for the three
 *       BUFFERABLE_ON_SUSPEND intents (production behaviour).
 *   <li><b>worst-case, empty CF</b> — {@code =true}: the lookup runs on every command.
 *   <li><b>worst-case, seeded CF</b> — same, after seeding N suspended instances so the lookup hits
 *       a realistically-sized SUSPENDED_PROCESS_INSTANCES.
 * </ol>
 *
 * <p>Caveat: the processing-duration timer is global across all record types, so the per-command
 * delta here is diluted by non-process-instance records — read it as "engine-wide mean record
 * processing time shifts by X under always-on lookup", with the JMH benchmark giving the isolated
 * per-lookup figure. Single run, no repeats — directional.
 */
public final class ProcessInstanceSuspensionLookupThroughputBenchmark {

  private static final String THROUGHPUT_PROCESS = "throughputProcess";
  private static final String SEED_PROCESS = "seedProcess";
  private static final String SEED_JOB_TYPE = "seed-task";
  private static final int WARMUP_INSTANCES = 500;
  private static final int WORKLOAD_INSTANCES = 1_500;
  private static final int SEED_SUSPENDED = 1_000;

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Before
  public void setUp() {
    RecordingExporter.setMaximumWaitTime(Duration.ofMinutes(3).toMillis());
    BpmnStreamProcessor.suspensionLookupAlwaysOn = false;
  }

  @After
  public void tearDown() {
    BpmnStreamProcessor.suspensionLookupAlwaysOn = false;
  }

  @Test
  public void measureAlwaysOnLookupTax() {
    // straight-through process: create() alone drives it to completion (no worker needed),
    // producing several PROCESS_INSTANCE commands per instance, incl. bufferable intents.
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(THROUGHPUT_PROCESS).startEvent().endEvent().done())
        .deploy();
    // service-task process for seeding: stays active (job never completed) so it can be suspended.
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(SEED_PROCESS)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(SEED_JOB_TYPE))
                .endEvent()
                .done())
        .deploy();

    // warm up the JIT before any measured phase
    drive(WARMUP_INSTANCES);

    System.out.println(
        "\nphase,suspensionLookupAlwaysOn,seededSuspended,recordsProcessed,meanProcessingNs");

    BpmnStreamProcessor.suspensionLookupAlwaysOn = false;
    measurePhase("control", 0);

    BpmnStreamProcessor.suspensionLookupAlwaysOn = true;
    measurePhase("worst-case-empty-cf", 0);

    seedSuspendedInstances(SEED_SUSPENDED);
    BpmnStreamProcessor.suspensionLookupAlwaysOn = true;
    measurePhase("worst-case-seeded-cf", SEED_SUSPENDED);
  }

  private void measurePhase(final String label, final int seeded) {
    // Bound RecordingExporter's in-memory list so the per-create await stays cheap (it scans from
    // index 0); the engine meter registry we read from is unaffected by this test-side reset.
    RecordingExporter.reset();
    final long countBefore = totalProcessedRecords();
    final double totalNsBefore = totalProcessingNs();

    drive(WORKLOAD_INSTANCES);

    final long deltaCount = totalProcessedRecords() - countBefore;
    final double deltaNs = totalProcessingNs() - totalNsBefore;
    final long meanNs = deltaCount == 0 ? 0 : Math.round(deltaNs / deltaCount);

    System.out.printf(
        "%s,%b,%d,%d,%d%n",
        label, BpmnStreamProcessor.suspensionLookupAlwaysOn, seeded, deltaCount, meanNs);
  }

  /** Fires {@code count} straight-through instances and waits until the last one completes. */
  private void drive(final int count) {
    long lastKey = 0;
    for (int i = 0; i < count; i++) {
      lastKey = engine.processInstance().ofBpmnProcessId(THROUGHPUT_PROCESS).create();
    }
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(lastKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  /** Seeds the SUSPENDED_PROCESS_INSTANCES CF with {@code count} suspended instances. */
  private void seedSuspendedInstances(final int count) {
    RecordingExporter.reset();
    for (int i = 0; i < count; i++) {
      final long key = engine.processInstance().ofBpmnProcessId(SEED_PROCESS).create();
      RecordingExporter.jobRecords()
          .withProcessInstanceKey(key)
          .withIntent(io.camunda.zeebe.protocol.record.intent.JobIntent.CREATED)
          .getFirst();
      engine.processInstance().withInstanceKey(key).suspend();
    }
  }

  // PROCESSING_DURATION is registered once per (valueType, intent) tag pair, so aggregate across
  // every matching timer to get the engine-wide record count and total processing time.
  private long totalProcessedRecords() {
    long count = 0;
    for (final Timer t :
        engine.getMeterRegistry().find("zeebe.stream.processor.processing.duration").timers()) {
      count += t.count();
    }
    return count;
  }

  private double totalProcessingNs() {
    double ns = 0;
    for (final Timer t :
        engine.getMeterRegistry().find("zeebe.stream.processor.processing.duration").timers()) {
      ns += t.totalTime(TimeUnit.NANOSECONDS);
    }
    return ns;
  }
}
