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
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
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
 * Benchmarks the overhead of SuspensionCheck.resolve() on the engine hot-path with varying numbers
 * of suspended process instances in state. Each benchmark iteration creates one new process
 * instance (targeting a non-suspended PI) and measures throughput. The suspension check performs a
 * RocksDB point-read on the SUSPENDED_PROCESS_INSTANCES column family for every SuspensionAware
 * command.
 */
@Warmup(iterations = 50, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 25, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class SuspensionCheckBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(SuspensionCheckBenchmark.class.getName());

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("task").done())
          .endEvent()
          .done();

  @Param({"0", "10000", "100000", "1000000"})
  private int suspendedInstanceCount;

  private TestContext testContext;
  private TestEngine engine;
  private ProcessInstanceClient processInstanceClient;

  @Setup
  public void setup() throws Throwable {
    testContext = TestEngine.createTestContext();
    engine = TestEngine.createSinglePartitionEngine(testContext);

    engine.createDeploymentClient().withXmlResource(PROCESS).deploy();
    processInstanceClient = engine.createProcessInstanceClient();

    LOG.info("Creating and suspending {} process instances...", suspendedInstanceCount);
    for (int i = 0; i < suspendedInstanceCount; i++) {
      final long piKey = processInstanceClient.ofBpmnProcessId("process").create();

      RecordingExporter.jobRecords()
          .withIntent(JobIntent.CREATED)
          .withProcessInstanceKey(piKey)
          .getFirst();

      processInstanceClient.withInstanceKey(piKey).suspend();

      RecordingExporter.reset();
      if (i > 0 && i % 10000 == 0) {
        LOG.info("\t{} instances suspended.", i);
        engine.reset();
      }
    }

    LOG.info("Setup complete. {} suspended PIs in state.", suspendedInstanceCount);
    engine.reset();
  }

  @TearDown
  public void tearDown() {
    testContext.autoCloseableRule().after();
    testContext.temporaryFolder().delete();
  }

  @Benchmark
  public Record<JobRecordValue> measureProcessCreationThroughput() {
    final long piKey = processInstanceClient.ofBpmnProcessId("process").create();

    final Record<JobRecordValue> task =
        RecordingExporter.jobRecords()
            .withIntent(JobIntent.CREATED)
            .withType("task")
            .withProcessInstanceKey(piKey)
            .getFirst();

    engine.reset();
    return task;
  }

  @JMHTest("measureProcessCreationThroughput")
  void shouldMeasureSuspensionCheckOverhead(final JMHTestCase testCase) {
    testCase.run();
  }
}
