/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.perf;

import io.camunda.zeebe.engine.perf.TestEngine.TestContext;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Benchmarks job activation throughput with and without suspended jobs in state. Suspended jobs are
 * removed from the activatable-by-priority index, so JobBatchActivateProcessor should not be
 * affected by their presence. This is a negative benchmark confirming the index design works.
 */
@Warmup(iterations = 50, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 25, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class JobActivationWithSuspendedJobsBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(JobActivationWithSuspendedJobsBenchmark.class.getName());

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("task").done())
          .endEvent()
          .done();

  @Param({"0", "10000", "100000"})
  private int suspendedJobCount;

  private TestContext testContext;
  private TestEngine engine;
  private ProcessInstanceClient processInstanceClient;

  @Setup
  public void setup() throws Throwable {
    testContext = TestEngine.createTestContext();
    engine = TestEngine.createSinglePartitionEngine(testContext);

    engine.createDeploymentClient().withXmlResource(PROCESS).deploy();
    processInstanceClient = engine.createProcessInstanceClient();

    LOG.info("Creating {} PIs to suspend (one job each)...", suspendedJobCount);
    for (int i = 0; i < suspendedJobCount; i++) {
      final long piKey = processInstanceClient.ofBpmnProcessId("process").create();

      RecordingExporter.jobRecords()
          .withIntent(JobIntent.CREATED)
          .withProcessInstanceKey(piKey)
          .getFirst();

      processInstanceClient.withInstanceKey(piKey).suspend();

      RecordingExporter.reset();
      if (i > 0 && i % 10000 == 0) {
        LOG.info("\t{} PIs suspended.", i);
        engine.reset();
      }
    }

    LOG.info("Setup complete. {} suspended jobs in state.", suspendedJobCount);
    engine.reset();
  }

  @TearDown
  public void tearDown() {
    testContext.autoCloseableRule().after();
    testContext.temporaryFolder().delete();
  }

  @Benchmark
  public Record<JobBatchRecordValue> measureJobActivationThroughput() {
    final long piKey = processInstanceClient.ofBpmnProcessId("process").create();

    RecordingExporter.jobRecords()
        .withIntent(JobIntent.CREATED)
        .withType("task")
        .withProcessInstanceKey(piKey)
        .getFirst();

    final Record<JobBatchRecordValue> activated =
        engine.createJobActivationClient().withType("task").withMaxJobsToActivate(1).activate();

    engine.reset();
    return activated;
  }

  @JMHTest("measureJobActivationThroughput")
  void shouldMeasureJobActivationWithSuspendedJobs(final JMHTestCase testCase) {
    testCase.run();
  }
}
