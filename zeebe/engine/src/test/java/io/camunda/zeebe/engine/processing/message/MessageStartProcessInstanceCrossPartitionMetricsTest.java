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
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Multi-partition metric assertions for the cross-partition message-start handshake counters M1
 * (REQUEST outcomes on {@code P_B}), M2 (reply outcomes on {@code P_K}) and M3 (asks dispatched
 * from {@code P_K}). This class drives the same scenarios as {@link
 * MessageStartProcessInstanceCrossPartitionHandshakeTest} and only asserts that the meters are
 * recorded on the expected partition registries.
 */
public final class MessageStartProcessInstanceCrossPartitionMetricsTest {

  private static final int PARTITION_COUNT = 3;

  // Same stable routing constants as the handshake test: hash("ck-1") and hash("biz-1") land on
  // different partitions so the cross-partition arm is exercised (re-asserted at @Before).
  private static final String CORRELATION_KEY = "ck-1";
  private static final String BUSINESS_ID = "biz-1";

  private static final String PROCESS_ID = "wf-cross";
  private static final String MESSAGE_NAME = "start-msg";
  private static final String START_EVENT_ID = "msgStart";

  private static final String REQUESTS_METRIC =
      "zeebe.message.start.cross.partition.requests.total";
  private static final String REPLIES_METRIC = "zeebe.message.start.cross.partition.replies.total";
  private static final String ASKS_METRIC = "zeebe.message.start.cross.partition.asks.total";

  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent(START_EVENT_ID)
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  private static final BpmnModelInstance DUAL_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("noneStart")
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .moveToProcess(PROCESS_ID)
          .startEvent(START_EVENT_ID)
          .message(MESSAGE_NAME)
          .connectTo("task")
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

  @Before
  public void assertCrossPartitionRouting() {
    assertThat(partitionFor(CORRELATION_KEY))
        .as(
            "CORRELATION_KEY (%s) and BUSINESS_ID (%s) must hash to different partitions so the"
                + " cross-partition arm is actually exercised",
            CORRELATION_KEY, BUSINESS_ID)
        .isNotEqualTo(partitionFor(BUSINESS_ID));
  }

  @Test
  public void shouldRecordAskRequestAndReplyMetricsForCleanCrossPartitionStart() {
    // given
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(MESSAGE_START_PROCESS);

    // when a message-start publish lands on P_K but its businessId hashes to P_B
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    // and the handshake completes: the PI is started on P_B and P_K processes the STARTED reply
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withElementType(BpmnElementType.PROCESS)
        .withBpmnProcessId(PROCESS_ID)
        .getFirst();
    RecordingExporter.messageStartProcessInstanceRequestRecords(
            MessageStartProcessInstanceRequestIntent.STARTED)
        .getFirst();

    // then the ask is counted on P_K (M3), the REQUEST is counted as started on P_B (M1), and the
    // STARTED reply is counted on P_K (M2)
    final int pK = partitionFor(CORRELATION_KEY);
    final int pB = partitionFor(BUSINESS_ID);
    assertThat(counter(pK, ASKS_METRIC, null, null))
        .as("M3: the cross-partition ask is dispatched from P_K")
        .isEqualTo(1.0);
    assertThat(counter(pB, REQUESTS_METRIC, "outcome", "started"))
        .as("M1: the REQUEST is decided as a clean start on P_B")
        .isEqualTo(1.0);
    assertThat(counter(pK, REPLIES_METRIC, "outcome", "started"))
        .as("M2: the STARTED reply is processed on P_K")
        .isEqualTo(1.0);
  }

  @Test
  public void shouldRecordUniquenessRejectionRequestAndReplyMetrics() {
    // given a holder PI already owns the businessId on P_B
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(DUAL_START_PROCESS);
    final long holderKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .onPartition(partitionFor(BUSINESS_ID))
            .withBusinessId(BUSINESS_ID)
            .create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .filter(r -> r.getValue().getProcessInstanceKey() == holderKey)
        .getFirst();

    // when a message-start publish asks P_B for the same businessId
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .withTimeToLive(0L)
        .publish();

    // and P_B replies UNIQUENESS_REJECTED
    RecordingExporter.messageStartProcessInstanceRequestRecords(
            MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED)
        .getFirst();

    // then the uniqueness rejection is counted on both the REQUEST (M1) and reply (M2) sides
    final int pK = partitionFor(CORRELATION_KEY);
    final int pB = partitionFor(BUSINESS_ID);
    assertThat(counter(pB, REQUESTS_METRIC, "outcome", "rejected_uniqueness"))
        .as("M1: the REQUEST is rejected for businessId uniqueness on P_B")
        .isGreaterThanOrEqualTo(1.0);
    assertThat(counter(pK, REPLIES_METRIC, "outcome", "rejected_uniqueness"))
        .as("M2: the uniqueness-rejection reply is processed on P_K")
        .isGreaterThanOrEqualTo(1.0);
    assertThat(counter(pK, ASKS_METRIC, null, null))
        .as("M3: at least one ask was dispatched from P_K")
        .isGreaterThanOrEqualTo(1.0);
  }

  private double counter(
      final int partition, final String name, final String tagKey, final String tagValue) {
    var search = engine.getMeterRegistry(partition).find(name);
    if (tagKey != null) {
      search = search.tag(tagKey, tagValue);
    }
    final var counter = search.counter();
    return counter != null ? counter.count() : 0.0;
  }

  private void deployAndAwaitStartEventSubscriptionsOnAllPartitions(
      final BpmnModelInstance process) {
    engine.deployment().withXmlResource(process).deploy();
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(MESSAGE_NAME)
        .limit(PARTITION_COUNT)
        .asList();
  }

  private static int partitionFor(final String key) {
    return SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString(key), PARTITION_COUNT);
  }
}
