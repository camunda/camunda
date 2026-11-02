/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.perf;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.perf.TestEngine.TestContext;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stress test checking that SUSPEND stays within the 4 MB batch limit
 * (ExceededBatchRecordSizeException) as the number of active elements grows. Three configurations:
 * jobs only, subscriptions only, and both. Jobs are suspended by the element instance walk, one per
 * batch, so job count alone does not grow any batch. Subscriptions are still closed in the SUSPEND
 * command's own batch, so the subscription sizes guard that batch against a regression.
 */
@Tag("performance")
class SuspensionBatchSizeTest {

  private static final Logger LOG =
      LoggerFactory.getLogger(SuspensionBatchSizeTest.class.getName());

  private static final long GENEROUS_WAIT_TIME_MS = Duration.ofSeconds(30).toMillis();

  @ParameterizedTest
  @ValueSource(ints = {100, 1000, 2000, 5000, 10000})
  void shouldSuspendInstanceWithOnlyJobs(final int elementCount) throws Throwable {
    final TestContext ctx = TestEngine.createTestContext();
    try {
      final TestEngine engine = TestEngine.createSinglePartitionEngine(ctx);
      final ProcessInstanceClient piClient = engine.createProcessInstanceClient();
      resetExporter();

      // given
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("jobOnly")
              .startEvent()
              .serviceTask(
                  "task",
                  t ->
                      t.zeebeJobType("task")
                          .multiInstance()
                          .parallel()
                          .zeebeInputCollectionExpression(
                              TestEngine.collectionExpression(elementCount))
                          .multiInstanceDone()
                          .done())
              .endEvent()
              .done();

      engine.createDeploymentClient().withXmlResource(process).deploy();
      final long piKey = piClient.ofBpmnProcessId("jobOnly").create();

      RecordingExporter.jobRecords()
          .withIntent(JobIntent.CREATED)
          .withProcessInstanceKey(piKey)
          .limit(elementCount)
          .count();

      engine.reset();
      resetExporter();

      // when
      final var result = trySuspend(piClient, piKey);
      logResult("Jobs only", elementCount, result);

      // then
      assertSuspended(result);
    } finally {
      ctx.close();
      RecordingExporter.reset();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 1000, 2000, 5000})
  void shouldSuspendInstanceWithOnlySubscriptions(final int elementCount) throws Throwable {
    final TestContext ctx = TestEngine.createTestContext();
    try {
      final TestEngine engine = TestEngine.createSinglePartitionEngine(ctx);
      final ProcessInstanceClient piClient = engine.createProcessInstanceClient();
      resetExporter();

      // given
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("subOnly")
              .startEvent()
              .receiveTask("receive")
              .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
              .multiInstance()
              .parallel()
              .zeebeInputCollectionExpression(TestEngine.collectionExpression(elementCount))
              .multiInstanceDone()
              .endEvent()
              .done();

      engine.createDeploymentClient().withXmlResource(process).deploy();
      final long piKey = piClient.ofBpmnProcessId("subOnly").withVariable("key", "k").create();

      RecordingExporter.processMessageSubscriptionRecords()
          .withIntent(ProcessMessageSubscriptionIntent.CREATED)
          .withProcessInstanceKey(piKey)
          .limit(elementCount)
          .count();

      engine.reset();
      resetExporter();

      // when
      final var result = trySuspend(piClient, piKey);
      logResult("Subscriptions only", elementCount, result);

      // then
      assertSuspended(result);
    } finally {
      ctx.close();
      RecordingExporter.reset();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 500, 1000, 2000, 5000})
  void shouldSuspendInstanceWithJobsAndSubscriptions(final int elementCount) throws Throwable {
    final TestContext ctx = TestEngine.createTestContext();
    try {
      final TestEngine engine = TestEngine.createSinglePartitionEngine(ctx);
      final ProcessInstanceClient piClient = engine.createProcessInstanceClient();
      resetExporter();

      // given
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("both")
              .startEvent()
              .subProcess(
                  "sub",
                  s ->
                      s.embeddedSubProcess()
                          .startEvent()
                          .parallelGateway("fork")
                          .serviceTask("task", t -> t.zeebeJobType("task").done())
                          .endEvent()
                          .moveToLastGateway()
                          .receiveTask("receive")
                          .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                          .endEvent()
                          .subProcessDone()
                          .multiInstance()
                          .parallel()
                          .zeebeInputCollectionExpression(
                              TestEngine.collectionExpression(elementCount))
                          .multiInstanceDone())
              .endEvent()
              .done();

      engine.createDeploymentClient().withXmlResource(process).deploy();
      final long piKey = piClient.ofBpmnProcessId("both").withVariable("key", "k").create();

      RecordingExporter.jobRecords()
          .withIntent(JobIntent.CREATED)
          .withProcessInstanceKey(piKey)
          .limit(elementCount)
          .count();

      RecordingExporter.processMessageSubscriptionRecords()
          .withIntent(ProcessMessageSubscriptionIntent.CREATED)
          .withProcessInstanceKey(piKey)
          .limit(elementCount)
          .count();

      engine.reset();
      resetExporter();

      // when
      final var result = trySuspend(piClient, piKey);
      logResult("Jobs + subscriptions", elementCount, result);

      // then
      assertSuspended(result);
    } finally {
      ctx.close();
      RecordingExporter.reset();
    }
  }

  private static void resetExporter() {
    RecordingExporter.reset();
    RecordingExporter.setMaximumWaitTime(GENEROUS_WAIT_TIME_MS);
  }

  private static Record<ProcessInstanceRecordValue> trySuspend(
      final ProcessInstanceClient piClient, final long piKey) {
    return piClient.withInstanceKey(piKey).onPartition(1).suspend();
  }

  private static void logResult(
      final String config,
      final int elementCount,
      final Record<ProcessInstanceRecordValue> result) {
    final boolean succeeded = result.getIntent() == ProcessInstanceIntent.SUSPENDED;
    if (succeeded) {
      LOG.info("{}, N={}: suspend SUCCEEDED", config, elementCount);
    } else {
      LOG.info(
          "{}, N={}: suspend FAILED ({}): {}",
          config,
          elementCount,
          result.getRejectionType(),
          result.getRejectionReason());
    }
  }

  private static void assertSuspended(final Record<ProcessInstanceRecordValue> result) {
    assertThat(result.getIntent()).isEqualTo(ProcessInstanceIntent.SUSPENDED);
  }
}
