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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
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
 * Benchmarks suspending and resuming a process instance with many active user tasks, alone and next
 * to a number of active jobs. User task suspension was added after the first suspend/resume
 * benchmarks, so this answers how much it adds to the suspend and resume time of an instance.
 *
 * <p>The {@code operation} param selects what is timed: {@code SUSPEND} times the suspend command
 * on a running instance, {@code RESUME} times the resume command on an instance that was suspended
 * during untimed setup.
 */
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class SuspendResumeUserTasksBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(SuspendResumeUserTasksBenchmark.class.getName());

  @Param({"1", "10", "100", "1000"})
  private int userTaskCount;

  /** Active jobs created next to the user tasks; 0 means user tasks only. */
  @Param({"0", "1000"})
  private int jobCount;

  @Param({"SUSPEND", "RESUME"})
  private Operation operation;

  private TestContext testContext;
  private TestEngine engine;
  private ProcessInstanceClient processInstanceClient;
  private long processInstanceKey;

  @Setup
  public void setup() throws Throwable {
    testContext = TestEngine.createTestContext();
    engine = TestEngine.createSinglePartitionEngine(testContext);

    engine.createDeploymentClient().withXmlResource(buildProcess()).deploy();
    processInstanceClient = engine.createProcessInstanceClient();
  }

  private BpmnModelInstance buildProcess() {
    final var userTasks = TestEngine.collectionExpression(userTaskCount);
    if (jobCount == 0) {
      return Bpmn.createExecutableProcess("process")
          .startEvent()
          .userTask(
              "userTask",
              t ->
                  t.zeebeUserTask()
                      .multiInstance()
                      .parallel()
                      .zeebeInputCollectionExpression(userTasks)
                      .multiInstanceDone())
          .endEvent()
          .done();
    }

    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .parallelGateway("fork")
        .userTask(
            "userTask",
            t ->
                t.zeebeUserTask()
                    .multiInstance()
                    .parallel()
                    .zeebeInputCollectionExpression(userTasks)
                    .multiInstanceDone())
        .parallelGateway("join")
        .endEvent()
        .moveToNode("fork")
        .serviceTask(
            "task",
            t ->
                t.zeebeJobType("task")
                    .multiInstance()
                    .parallel()
                    .zeebeInputCollectionExpression(TestEngine.collectionExpression(jobCount))
                    .multiInstanceDone())
        .connectTo("join")
        .done();
  }

  @TearDown
  public void tearDown() {
    testContext.close();
  }

  @Setup(Level.Invocation)
  public void setupProcessInstance() {
    LOG.info("Creating PI with {} user tasks and {} jobs...", userTaskCount, jobCount);
    // engine.reset() in the previous invocation's teardown restores the 5s default wait - raise the
    // ceiling before the CREATED awaits below. It only bounds how long a wait takes to fail; it
    // doesn't bias the timed call.
    RecordingExporter.setMaximumWaitTime(Duration.ofSeconds(30).toMillis());
    processInstanceKey = processInstanceClient.ofBpmnProcessId("process").create();

    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(userTaskCount)
        .count();
    if (jobCount > 0) {
      RecordingExporter.jobRecords()
          .withIntent(JobIntent.CREATED)
          .withProcessInstanceKey(processInstanceKey)
          .limit(jobCount)
          .count();
    }

    if (operation == Operation.RESUME) {
      processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).suspend();
    }
    LOG.info("PI {} ready.", processInstanceKey);
    // the timed call waits for its result by scanning the exporter from the start - drop the setup
    // records so that scan doesn't grow with userTaskCount and jobCount. reset() also restores the
    // 5s default wait, so raise it again for the timed call.
    RecordingExporter.reset();
    RecordingExporter.setMaximumWaitTime(Duration.ofSeconds(30).toMillis());
  }

  @TearDown(Level.Invocation)
  public void resetAfterInvocation() {
    // engine.reset() only clears the log/exporter, not RocksDB state - cancel this invocation's PI
    // first so state doesn't accumulate across the whole trial
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).cancel();
    engine.reset();
  }

  @Benchmark
  public long measureOperation() {
    final var instance = processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1);
    if (operation == Operation.SUSPEND) {
      instance.suspend();
    } else {
      instance.resume();
    }
    return processInstanceKey;
  }

  @JMHTest("measureOperation")
  void shouldMeasureSuspendWithUserTasksOnly(final JMHTestCase testCase) {
    testCase
        .withOptions(
            opts ->
                opts.param("userTaskCount", "100")
                    .param("jobCount", "0")
                    .param("operation", "SUSPEND"))
        .run();
  }

  @JMHTest("measureOperation")
  void shouldMeasureResumeWithUserTasksOnly(final JMHTestCase testCase) {
    testCase
        .withOptions(
            opts ->
                opts.param("userTaskCount", "100")
                    .param("jobCount", "0")
                    .param("operation", "RESUME"))
        .run();
  }

  @JMHTest("measureOperation")
  void shouldMeasureSuspendWithUserTasksAndJobs(final JMHTestCase testCase) {
    testCase
        .withOptions(
            opts ->
                opts.param("userTaskCount", "100")
                    .param("jobCount", "1000")
                    .param("operation", "SUSPEND"))
        .run();
  }

  @JMHTest("measureOperation")
  void shouldMeasureResumeWithUserTasksAndJobs(final JMHTestCase testCase) {
    testCase
        .withOptions(
            opts ->
                opts.param("userTaskCount", "100")
                    .param("jobCount", "1000")
                    .param("operation", "RESUME"))
        .run();
  }

  /** The command timed by {@link #measureOperation()}. */
  public enum Operation {
    /** Suspends a running instance. */
    SUSPEND,
    /** Resumes an instance that was suspended during untimed setup. */
    RESUME
  }
}
