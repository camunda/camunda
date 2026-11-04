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
import org.openjdk.jmh.annotations.Level;
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
 * Measures what the suspension check costs when creating a process instance. Each iteration creates
 * one instance and waits for its first job.
 *
 * <ul>
 *   <li>{@code createBy}: by process definition key, or by process id (latest active version)
 *   <li>{@code suspendedDefinitions}: other process definitions with a suspended version, which
 *       fills the marker column family
 *   <li>{@code latestSuspended}: the target process has 10 versions; when true the latest is
 *       suspended, so creation by id has to scan down to the previous version
 * </ul>
 */
@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class ProcessDefinitionSuspensionCheckBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessDefinitionSuspensionCheckBenchmark.class);
  private static final String PROCESS_ID = "process";
  private static final int VERSIONS = 10;

  @Param({"KEY", "ID"})
  private String createBy;

  @Param({"0", "1000"})
  private int suspendedDefinitions;

  @Param({"false", "true"})
  private boolean latestSuspended;

  private TestContext testContext;
  private TestEngine engine;
  private ProcessInstanceClient processInstanceClient;
  private long targetKey;
  private long processInstanceKey;

  @Setup
  public void setup() throws Throwable {
    testContext = TestEngine.createTestContext();
    engine = TestEngine.createSinglePartitionEngine(testContext);
    processInstanceClient = engine.createProcessInstanceClient();

    long latestKey = -1;
    long previousKey = -1;
    for (int version = 1; version <= VERSIONS; version++) {
      previousKey = latestKey;
      latestKey = deploy(PROCESS_ID, "version-" + version);
    }
    if (latestSuspended) {
      suspend(latestKey);
      targetKey = previousKey;
    } else {
      targetKey = latestKey;
    }

    LOG.info("Deploying and suspending {} other process definitions...", suspendedDefinitions);
    for (int i = 0; i < suspendedDefinitions; i++) {
      suspend(deploy("other-" + i, "other"));
      if (i > 0 && i % 500 == 0) {
        engine.reset();
      }
    }
    engine.reset();
  }

  private long deploy(final String processId, final String name) {
    return engine
        .createDeploymentClient()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .name(name)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  // The only place that needs the suspension feature; a baseline run on main stubs it out.
  private void suspend(final long processDefinitionKey) {
    engine.createProcessDefinitionSuspensionClient().withKey(processDefinitionKey).suspend();
  }

  @TearDown
  public void tearDown() {
    testContext.close();
  }

  @TearDown(Level.Invocation)
  public void resetAfterInvocation() {
    // engine.reset() only clears the log and exporter, not RocksDB state; cancel the instance so
    // state does not accumulate over the trial
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).cancel();
    engine.reset();
  }

  @Benchmark
  public Record<JobRecordValue> measureProcessCreationThroughput() {
    processInstanceKey =
        "KEY".equals(createBy)
            ? processInstanceClient.ofBpmnProcessId("").withProcessDefinitionKey(targetKey).create()
            : processInstanceClient.ofBpmnProcessId(PROCESS_ID).create();

    return RecordingExporter.jobRecords()
        .withIntent(JobIntent.CREATED)
        .withType("task")
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }

  @JMHTest("measureProcessCreationThroughput")
  void shouldMeasureKeyWithNoSuspended(final JMHTestCase testCase) {
    run(testCase, "KEY", 0, false);
  }

  @JMHTest("measureProcessCreationThroughput")
  void shouldMeasureKeyWith1000Suspended(final JMHTestCase testCase) {
    run(testCase, "KEY", 1000, false);
  }

  @JMHTest("measureProcessCreationThroughput")
  void shouldMeasureKeyWithLatestSuspended(final JMHTestCase testCase) {
    run(testCase, "KEY", 0, true);
  }

  @JMHTest("measureProcessCreationThroughput")
  void shouldMeasureKeyWith1000SuspendedAndLatestSuspended(final JMHTestCase testCase) {
    run(testCase, "KEY", 1000, true);
  }

  @JMHTest("measureProcessCreationThroughput")
  void shouldMeasureIdWithNoSuspended(final JMHTestCase testCase) {
    run(testCase, "ID", 0, false);
  }

  @JMHTest("measureProcessCreationThroughput")
  void shouldMeasureIdWith1000Suspended(final JMHTestCase testCase) {
    run(testCase, "ID", 1000, false);
  }

  @JMHTest("measureProcessCreationThroughput")
  void shouldMeasureIdWithLatestSuspended(final JMHTestCase testCase) {
    run(testCase, "ID", 0, true);
  }

  @JMHTest("measureProcessCreationThroughput")
  void shouldMeasureIdWith1000SuspendedAndLatestSuspended(final JMHTestCase testCase) {
    run(testCase, "ID", 1000, true);
  }

  private static void run(
      final JMHTestCase testCase,
      final String createBy,
      final int suspendedDefinitions,
      final boolean latestSuspended) {
    testCase
        .withOptions(
            opts ->
                opts.param("createBy", createBy)
                    .param("suspendedDefinitions", String.valueOf(suspendedDefinitions))
                    .param("latestSuspended", String.valueOf(latestSuspended)))
        .run();
  }
}
