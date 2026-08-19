/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers the TTL-correct defer + resume-repoll mechanism that replaces the generic buffer-verbatim
 * suspension gate for {@code ProcessMessageSubscriptionIntent#CORRELATE} (see {@link
 * ProcessMessageSubscriptionCorrelateProcessor}). Message correlation that is mid-flight when a
 * process instance suspends must be deferred, not blindly replayed on resume: whether it ultimately
 * correlates depends on the message's TTL at the time of resume, not at the time it first tried to
 * correlate.
 */
public final class MessageSuspensionDeferralTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCorrelateDeferredMessageWithValidTtlOnResume() {
    // given - an instance waiting on an open catch event, suspended
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(processId, messageName, correlationKey);
    awaitSubscriptionCreated(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - a message with a long TTL is published while suspended; it starts correlating on the
    // message partition, deferred once it reaches the suspended instance
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(10))
        .publish();

    final var deferred =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CORRELATION_DEFERRED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(deferred.getValue().getMessageName()).isEqualTo(messageName);

    // and - forward progress is genuinely blocked: the catch event has not completed
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .processInstanceRecords()
                        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                        .withProcessInstanceKey(processInstanceKey)
                        .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
                        .exists()))
        .isFalse();

    // when - the instance resumes
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - the still-valid message correlates and the process completes
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  @Test
  public void shouldNotCorrelateDeferredMessageWhoseTtlExpiredBeforeResume() {
    // given - an instance waiting on an open catch event, suspended
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(processId, messageName, correlationKey);
    awaitSubscriptionCreated(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - a short-TTL message is published while suspended and deferred, then its TTL expires
    // before resume (well short of the message TTL checker interval, so it is still in state -
    // just past its deadline)
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMillis(50))
        .publish();
    RecordingExporter.processMessageSubscriptionRecords(
            ProcessMessageSubscriptionIntent.CORRELATION_DEFERRED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.increaseTime(Duration.ofMillis(500));

    // and - the instance resumes
    final var resumed = ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - the resume completes, but the expired message never correlates and the instance
    // keeps waiting at the catch event
    Assertions.assertThat(resumed).hasIntent(ProcessInstanceIntent.RESUMED);
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .processMessageSubscriptionRecords()
                        .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                        .withProcessInstanceKey(processInstanceKey)
                        .exists()))
        .isFalse();
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .processInstanceRecords()
                        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                        .withProcessInstanceKey(processInstanceKey)
                        .withElementType(BpmnElementType.PROCESS)
                        .exists()))
        .isFalse();
  }

  @Test
  public void shouldRedirectToActiveSiblingInsteadOfSuspendedInstance() {
    // given - two instances of the same process share a correlation key; one is suspended
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long suspendedInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(processId, messageName, correlationKey);
    awaitSubscriptionCreated(suspendedInstanceKey);
    final long activeInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    awaitSubscriptionCreated(activeInstanceKey);
    ENGINE.processInstance().withInstanceKey(suspendedInstanceKey).suspend();

    // when
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(10))
        .publish();

    // then - the active sibling correlates and completes; the suspended one only ever sees the
    // deferral, never a correlation of the same message, even after it resumes
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(activeInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    RecordingExporter.processMessageSubscriptionRecords(
            ProcessMessageSubscriptionIntent.CORRELATION_DEFERRED)
        .withProcessInstanceKey(suspendedInstanceKey)
        .await();

    ENGINE.processInstance().withInstanceKey(suspendedInstanceKey).resume();

    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .processMessageSubscriptionRecords()
                        .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                        .withProcessInstanceKey(suspendedInstanceKey)
                        .exists()))
        .isFalse();
  }

  @Test
  public void shouldCorrelateAllValidBufferedMessagesInOrderOnResumeForNonInterruptingBoundary() {
    // given - a non-interrupting boundary message event, so its subscription stays OPENED and
    // keeps re-triggering after each correlation (see MessageSubscriptionCorrelateProcessor's
    // non-interrupting chaining, reused as-is by the resume retry)
    final String processId = Strings.newRandomValidBpmnId();
    final String jobType = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .boundaryEvent(
                    "boundary",
                    b ->
                        b.cancelActivity(false)
                            .message(
                                m ->
                                    m.name(messageName)
                                        .zeebeCorrelationKey("=\"%s\"".formatted(correlationKey))))
                .endEvent("boundaryEnd")
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    awaitSubscriptionCreated(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - three valid-TTL messages accumulate while suspended, each deferred in turn
    for (int i = 0; i < 3; i++) {
      ENGINE
          .message()
          .withName(messageName)
          .withCorrelationKey(correlationKey)
          .withTimeToLive(Duration.ofMinutes(10))
          .publish();
    }
    RecordingExporter.processMessageSubscriptionRecords(
            ProcessMessageSubscriptionIntent.CORRELATION_DEFERRED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(3)
        .await();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - all three still-valid messages correlate, in the order they were published, chained
    // through the existing non-interrupting re-poll rather than needing a retry per message
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .extracting(r -> r.getValue().getMessageKey())
        .isSorted();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .limit(3)
                .asList())
        .hasSize(3);
  }

  private long deployAndStartProcessWithMessageCatchEvent(
      final String processId, final String messageName, final String correlationKey) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "msg",
                    e ->
                        e.message(
                            m ->
                                m.name(messageName)
                                    .zeebeCorrelationKey("=\"%s\"".formatted(correlationKey))))
                .endEvent()
                .done())
        .deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).create();
  }

  private static Record<ProcessMessageSubscriptionRecordValue> awaitSubscriptionCreated(
      final long processInstanceKey) {
    return RecordingExporter.processMessageSubscriptionRecords(
            ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }
}
