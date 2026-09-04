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
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
 * Benchmarks the time to suspend a process instance with many active elements. Uses a parallel
 * multi-instance service task to create PIs with N active jobs. The suspend operation BFS-walks all
 * element instances and writes Job.SUSPENDED for each activatable job.
 */
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class SuspendJobsBenchmark {

  private static final Logger LOG = LoggerFactory.getLogger(SuspendJobsBenchmark.class.getName());

  @Param({"100", "500", "1000"})
  private int activeElementCount;

  private TestContext testContext;
  private TestEngine engine;
  private ProcessInstanceClient processInstanceClient;
  private long processInstanceKey;

  @Setup(Level.Invocation)
  public void setup() throws Throwable {
    testContext = TestEngine.createTestContext();
    engine = TestEngine.createSinglePartitionEngine(testContext);

    final String collectionExpression =
        "=["
            + IntStream.rangeClosed(1, activeElementCount)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","))
            + "]";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("task")
                        .multiInstance()
                        .parallel()
                        .zeebeInputCollectionExpression(collectionExpression)
                        .multiInstanceDone()
                        .done())
            .endEvent()
            .done();

    engine.createDeploymentClient().withXmlResource(process).deploy();
    processInstanceClient = engine.createProcessInstanceClient();

    LOG.info("Creating PI with {} active elements...", activeElementCount);
    processInstanceKey = processInstanceClient.ofBpmnProcessId("process").create();

    RecordingExporter.jobRecords()
        .withIntent(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(activeElementCount)
        .count();

    LOG.info("PI {} ready with {} jobs.", processInstanceKey, activeElementCount);
    engine.reset();
  }

  @TearDown(Level.Invocation)
  public void tearDown() {
    testContext.autoCloseableRule().after();
    testContext.temporaryFolder().delete();
  }

  @Benchmark
  public long measureSuspendTime() {
    processInstanceClient.withInstanceKey(processInstanceKey).onPartition(1).suspend();
    return processInstanceKey;
  }

  @JMHTest("measureSuspendTime")
  void shouldMeasureSuspendLatency(final JMHTestCase testCase) {
    testCase.run();
  }
}
