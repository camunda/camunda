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
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.DefaultActorClock;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.rules.TemporaryFolder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Warmup(iterations = 100, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 50, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class EngineLargeStatePerformanceTest {
  public static final Logger LOG =
      LoggerFactory.getLogger(EngineLargeStatePerformanceTest.class.getName());

  private long count;
  private ProcessInstanceClient processInstanceClient;
  private TestEngine.TestContext testContext;
  private TestEngine singlePartitionEngine;

  @Setup
  public void setup() throws Throwable {
    testContext = createTestContext();

    singlePartitionEngine = TestEngine.createSinglePartitionEngine(testContext);

    setupState(singlePartitionEngine);
  }

  /** Will build up a state for the large state performance test */
  private void setupState(final TestEngine singlePartitionEngine) {
    singlePartitionEngine
        .createDeploymentClient()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", (t) -> t.zeebeJobType("task").done())
                .endEvent()
                .done())
        .deploy();

    processInstanceClient = singlePartitionEngine.createProcessInstanceClient();

    final int maxInstanceCount = 200_000;
    LOG.info("Starting {} process instances, please hold the line...", maxInstanceCount);
    for (int i = 0; i < maxInstanceCount; i++) {
      processInstanceClient.ofBpmnProcessId("process").create();
      count++;
      RecordingExporter.reset();

      if ((i % 10000) == 0) {
        LOG.info("\t{} process instances already started.", i);
        singlePartitionEngine.reset();
      }
    }

    LOG.info("Started {} process instances.", count);
  }

  private TestEngine.TestContext createTestContext() throws IOException {
    final var autoCloseableRule = new AutoCloseableRule();
    final var temporaryFolder = new TemporaryFolder();
    temporaryFolder.create();

    // scheduler
    final var builder =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .setActorClock(new DefaultActorClock());

    final var actorScheduler = builder.build();
    autoCloseableRule.manage(actorScheduler);
    actorScheduler.start();
    return new TestContext(actorScheduler, temporaryFolder, autoCloseableRule);
  }

  @TearDown
  public void tearDown() {
    LOG.info("Started {} process instances", count);
    testContext.autoCloseableRule().after();
  }

  @Benchmark
  public Record<?> measureProcessExecutionTime() {
    final long piKey = processInstanceClient.ofBpmnProcessId("process").create();

    final Record<JobRecordValue> task =
        RecordingExporter.jobRecords()
            .withIntent(JobIntent.CREATED)
            .withType("task")
            .withProcessInstanceKey(piKey)
            .getFirst();

    count++;
    singlePartitionEngine.reset();
    return task;
  }

  @JMHTest("measureProcessExecutionTime")
  void shouldProcessWithinExpectedDeviation(final JMHTestCase testCase) {
    // given - an expected ops/s score, as measured in CI
    // when running this test locally, you're likely to have a different score
    final var referenceScore = 1000;

    // when
    final var assertResult = testCase.run();

    // then
    assertResult.isAtLeast(referenceScore, 0.25);
  }
}
