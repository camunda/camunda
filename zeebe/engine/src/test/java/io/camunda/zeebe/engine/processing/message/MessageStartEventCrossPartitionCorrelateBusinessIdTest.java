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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Multi-partition behavioral pin for the <em>synchronous correlate</em> arm of the cross-partition
 * message-start handshake (issue #58207). Unlike a buffered publish, a {@code POST
 * /messages/correlation} command has a waiting client, so the deferred cross-partition reply
 * ({@code STARTED} / {@code UNIQUENESS_REJECTED} / {@code NO_SUBSCRIPTION_REJECTED}) must be
 * reflected back into the correlate response instead of a spurious {@code NOT_FOUND}.
 *
 * <p>The constants are chosen so {@code hash(correlationKey)} and {@code hash(businessId)} land on
 * different partitions, exercising the cross-partition routing flip; a precondition check in {@link
 * #assertCrossPartitionRouting()} fails loudly if a hash-function change degenerates the scenario
 * into a single-partition path.
 */
public final class MessageStartEventCrossPartitionCorrelateBusinessIdTest {

  private static final int PARTITION_COUNT = 3;

  // hash("ck-1") -> P_K = partition 1, hash("biz-1") -> P_B = partition 3 (PARTITION_COUNT = 3),
  // so the correlate is routed to a different partition than the one that owns the businessId.
  private static final String CORRELATION_KEY = "ck-1";
  // A second correlation key on yet another partition (hash("ck-2") -> partition 2). Used by the
  // uniqueness-rejection test so the second correlate does NOT collide with the local
  // correlation-key lock written for CORRELATION_KEY on P_K, and is therefore delegated to P_B —
  // exercising the async UNIQUENESS_REJECTED reply arm rather than the synchronous local-lock path.
  private static final String SECOND_CORRELATION_KEY = "ck-2";
  private static final String BUSINESS_ID = "biz-1";

  private static final String PROCESS_ID = "wf-cross-correlate";
  private static final String MESSAGE_NAME = "start-msg-correlate";
  private static final String START_EVENT_ID = "msgStart";

  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent(START_EVENT_ID)
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

  @Before
  public void assertCrossPartitionRouting() {
    assertThat(partitionFor(CORRELATION_KEY))
        .describedAs(
            """
                CORRELATION_KEY (%s) and BUSINESS_ID (%s) must hash to different partitions so the\
                 cross-partition correlate arm is actually exercised; if this fails after a hash\
                 change, pick new constants""",
            CORRELATION_KEY, BUSINESS_ID)
        .isNotEqualTo(partitionFor(BUSINESS_ID));
    assertThat(partitionFor(SECOND_CORRELATION_KEY))
        .describedAs(
            """
                SECOND_CORRELATION_KEY (%s) must hash to a partition other than P_B (%s) and other than\
                 CORRELATION_KEY (%s), so the second correlate is delegated to P_B and does not\
                 collide with the local correlation-key lock""",
            SECOND_CORRELATION_KEY, BUSINESS_ID, CORRELATION_KEY)
        .isNotEqualTo(partitionFor(BUSINESS_ID))
        .isNotEqualTo(partitionFor(CORRELATION_KEY));
  }

  @Test
  public void shouldCorrelateToMessageStartEventAcrossPartitionsAndReturnCorrelated() {
    // given
    deployAndAwaitStartEventSubscriptionsOnAllPartitions();

    // when a synchronous correlate lands on P_K but its businessId hashes to P_B
    final var correlated =
        engine
            .messageCorrelation()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .withBusinessId(BUSINESS_ID)
            .correlate();

    // then
    assertThat(correlated.getValue().getProcessInstanceKey())
        .describedAs("the correlate is answered CORRELATED on P_K, carrying the created PI key")
        .isPositive();
    assertThat(correlated.getPartitionId())
        .describedAs("the correlate is answered on P_K = hash(correlationKey)")
        .isEqualTo(partitionFor(CORRELATION_KEY));

    final var activating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .withBpmnProcessId(PROCESS_ID)
            .getFirst();
    assertThat(Protocol.decodePartitionId(activating.getValue().getProcessInstanceKey()))
        .describedAs("the new PI lives on P_B = hash(businessId)")
        .isEqualTo(partitionFor(BUSINESS_ID));
    assertThat(activating.getValue().getProcessInstanceKey())
        .describedAs("the correlate response carries the PI key created on P_B")
        .isEqualTo(correlated.getValue().getProcessInstanceKey());
    assertThat(activating.getValue().getBusinessId())
        .describedAs("the new PI carries the businessId")
        .isEqualTo(BUSINESS_ID);
  }

  @Test
  public void shouldRejectDuplicateBusinessIdCorrelateWhileHolderIsActive() {
    // given a first correlate started a holder PI on P_B
    deployAndAwaitStartEventSubscriptionsOnAllPartitions();
    engine
        .messageCorrelation()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .correlate();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementType(BpmnElementType.PROCESS)
        .withBpmnProcessId(PROCESS_ID)
        .await();

    // when a second correlate reuses the same businessId while the holder is still active. It uses
    // a different correlationKey so it is not blocked by the local correlation-key lock and is
    // instead delegated to P_B, which rejects it on businessId uniqueness.
    final var notCorrelated =
        engine
            .messageCorrelation()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(SECOND_CORRELATION_KEY)
            .withBusinessId(BUSINESS_ID)
            .expectNotCorrelated()
            .correlate();

    // then
    assertThat(notCorrelated.getIntent())
        .describedAs(
            "the second correlate is answered NOT_CORRELATED rather than starting a second instance")
        .isEqualTo(MessageCorrelationIntent.NOT_CORRELATED);
    assertThat(notCorrelated.getPartitionId())
        .describedAs("the NOT_CORRELATED response is returned on its own P_K = hash(secondKey)")
        .isEqualTo(partitionFor(SECOND_CORRELATION_KEY));

    final var processInstancesActivating =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getIntent() == MessageCorrelationIntent.NOT_CORRELATED
                        && r.getKey() == notCorrelated.getKey())
            .processInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .count();
    assertThat(processInstancesActivating)
        .describedAs("the active holder blocks the second correlate; no second instance is created")
        .isEqualTo(1);

    final var expiredMessage =
        RecordingExporter.messageRecords(MessageIntent.EXPIRED)
            .withPartitionId(partitionFor(SECOND_CORRELATION_KEY))
            .withName(MESSAGE_NAME)
            .getFirst();
    assertThat(expiredMessage.getKey())
        .describedAs(
            "the fire-and-forget correlate message is expired, not retained for retry, so the "
                + "rejected start is never retried into a late instance (regression guard for #58250)")
        .isEqualTo(notCorrelated.getKey());
  }

  private void deployAndAwaitStartEventSubscriptionsOnAllPartitions() {
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    // Wait until every partition has the MessageStartEventSubscription CREATED so the ask processor
    // on P_B sees its local subscription before the first cross-partition request arrives.
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
