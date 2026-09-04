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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stress test finding the number of active elements at which SUSPEND hits
 * ExceededBatchRecordSizeException (4 MB batch limit). Three configurations: jobs only,
 * subscriptions only, and both. Each size asserts the previously-observed outcome (success or
 * EXCEEDED_BATCH_RECORD_SIZE rejection) so a regression in the batch limit is caught rather than
 * only logged.
 */
@Tag("performance")
class SuspensionBatchLimitTest {

  private static final Logger LOG =
      LoggerFactory.getLogger(SuspensionBatchLimitTest.class.getName());

  private static final long GENEROUS_WAIT_TIME_MS = Duration.ofSeconds(30).toMillis();

  static Stream<Arguments> jobOnlySizes() {
    return Stream.of(
        Arguments.of(100, false),
        Arguments.of(500, false),
        Arguments.of(1000, false),
        Arguments.of(2000, false),
        Arguments.of(5000, true),
        Arguments.of(10000, true));
  }

  static Stream<Arguments> subscriptionOnlySizes() {
    return Stream.of(
        Arguments.of(100, false),
        Arguments.of(500, false),
        Arguments.of(1000, false),
        Arguments.of(2000, false),
        Arguments.of(5000, false));
  }

  static Stream<Arguments> bothSizes() {
    return Stream.of(
        Arguments.of(100, false),
        Arguments.of(500, false),
        Arguments.of(1000, false),
        Arguments.of(2000, false),
        Arguments.of(5000, true));
  }

  @ParameterizedTest
  @MethodSource("jobOnlySizes")
  void shouldFindJobOnlyBatchLimit(final int elementCount, final boolean expectRejection)
      throws Throwable {
    final TestContext ctx = TestEngine.createTestContext();
    try {
      final TestEngine engine = TestEngine.createSinglePartitionEngine(ctx);
      final ProcessInstanceClient piClient = engine.createProcessInstanceClient();
      RecordingExporter.setMaximumWaitTime(GENEROUS_WAIT_TIME_MS);

      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("jobOnly")
              .startEvent()
              .serviceTask(
                  "task",
                  t ->
                      t.zeebeJobType("task")
                          .multiInstance()
                          .parallel()
                          .zeebeInputCollectionExpression(buildCollectionExpression(elementCount))
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
      RecordingExporter.setMaximumWaitTime(GENEROUS_WAIT_TIME_MS);
      final var result = trySuspend(piClient, piKey);
      logResult("Jobs only", elementCount, result);
      assertSuspendOutcome(result, expectRejection);
    } finally {
      ctx.autoCloseableRule().after();
      ctx.temporaryFolder().delete();
    }
  }

  @ParameterizedTest
  @MethodSource("subscriptionOnlySizes")
  void shouldFindSubscriptionOnlyBatchLimit(final int elementCount, final boolean expectRejection)
      throws Throwable {
    final TestContext ctx = TestEngine.createTestContext();
    try {
      final TestEngine engine = TestEngine.createSinglePartitionEngine(ctx);
      final ProcessInstanceClient piClient = engine.createProcessInstanceClient();
      RecordingExporter.setMaximumWaitTime(GENEROUS_WAIT_TIME_MS);

      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("subOnly")
              .startEvent()
              .receiveTask("receive")
              .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
              .multiInstance()
              .parallel()
              .zeebeInputCollectionExpression(buildCollectionExpression(elementCount))
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
      RecordingExporter.setMaximumWaitTime(GENEROUS_WAIT_TIME_MS);
      final var result = trySuspend(piClient, piKey);
      logResult("Subscriptions only", elementCount, result);
      assertSuspendOutcome(result, expectRejection);
    } finally {
      ctx.autoCloseableRule().after();
      ctx.temporaryFolder().delete();
    }
  }

  @ParameterizedTest
  @MethodSource("bothSizes")
  void shouldFindCombinedBatchLimit(final int elementCount, final boolean expectRejection)
      throws Throwable {
    final TestContext ctx = TestEngine.createTestContext();
    try {
      final TestEngine engine = TestEngine.createSinglePartitionEngine(ctx);
      final ProcessInstanceClient piClient = engine.createProcessInstanceClient();
      RecordingExporter.setMaximumWaitTime(GENEROUS_WAIT_TIME_MS);

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
                          .zeebeInputCollectionExpression(buildCollectionExpression(elementCount))
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
      RecordingExporter.setMaximumWaitTime(GENEROUS_WAIT_TIME_MS);
      final var result = trySuspend(piClient, piKey);
      logResult("Jobs + subscriptions", elementCount, result);
      assertSuspendOutcome(result, expectRejection);
    } finally {
      ctx.autoCloseableRule().after();
      ctx.temporaryFolder().delete();
    }
  }

  private static Record<ProcessInstanceRecordValue> trySuspend(
      final ProcessInstanceClient piClient, final long piKey) {
    try {
      return piClient.withInstanceKey(piKey).onPartition(1).suspend();
    } catch (final Exception e) {
      return RecordingExporter.processInstanceRecords()
          .withProcessInstanceKey(piKey)
          .withIntent(ProcessInstanceIntent.SUSPEND)
          .onlyCommandRejections()
          .getFirst();
    }
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

  private static void assertSuspendOutcome(
      final Record<ProcessInstanceRecordValue> result, final boolean expectRejection) {
    if (expectRejection) {
      assertThat(result.getRejectionType()).isEqualTo(RejectionType.EXCEEDED_BATCH_RECORD_SIZE);
    } else {
      assertThat(result.getIntent()).isEqualTo(ProcessInstanceIntent.SUSPENDED);
    }
  }

  private static String buildCollectionExpression(final int count) {
    return "=["
        + IntStream.rangeClosed(1, count)
            .mapToObj(Integer::toString)
            .collect(Collectors.joining(","))
        + "]";
  }
}
