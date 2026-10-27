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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Behavioural assertions for the {@code zeebe.message.start.blocked.total} counter (M14): a live
 * message-start correlate that finds a holder leaves the message buffered instead of starting a new
 * instance, and the block is attributed by the {@code reason} tag. A single partition is used so
 * every gate is evaluated locally and the businessId arm never delegates cross-partition.
 */
public final class MessageStartBlockedMetricsTest {

  private static final String BLOCKED_METRIC = "zeebe.message.start.blocked.total";

  private static final String PROCESS_ID = "wf-blocked";
  private static final String MESSAGE_NAME = "start-msg";
  private static final String JOB_TYPE = "test";

  // The holder started by the first publish; later publishes reuse or vary these to pick a reason.
  private static final String HELD_CORRELATION_KEY = "ck-held";
  private static final String HELD_BUSINESS_ID = "biz-held";
  private static final String FREE_CORRELATION_KEY = "ck-free";
  private static final String FREE_BUSINESS_ID = "biz-free";

  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("msgStart")
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

  @Before
  public void deployAndHoldBothGates() {
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(MESSAGE_NAME)
        .getFirst();

    // Start a holder instance that owns both the correlation key and the business id.
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(HELD_CORRELATION_KEY)
        .withBusinessId(HELD_BUSINESS_ID)
        .publish();

    // Wait until the holder is active (reached the service task) so both uniqueness indexes are
    // set.
    RecordingExporter.jobRecords(JobIntent.CREATED).withType(JOB_TYPE).getFirst();
  }

  @Test
  public void shouldCountBlockOnHeldCorrelationKeyOnly() {
    // when a publish reuses the held correlation key but carries a fresh business id
    publishBlocked(HELD_CORRELATION_KEY, FREE_BUSINESS_ID);

    // then the block is attributed to the correlation key alone (M14)
    assertThat(blockedCount("correlation_key")).isEqualTo(1.0);
    assertThat(blockedCount("business_id")).isZero();
  }

  @Test
  public void shouldCountBlockOnHeldBusinessIdOnly() {
    // when a publish carries a fresh correlation key but reuses the held business id
    publishBlocked(FREE_CORRELATION_KEY, HELD_BUSINESS_ID);

    // then the block is attributed to the business id alone (M14)
    assertThat(blockedCount("business_id")).isEqualTo(1.0);
    assertThat(blockedCount("correlation_key")).isZero();
  }

  @Test
  public void shouldAttributeBlockToCorrelationKeyWhenBothGatesHeld() {
    // when a publish reuses both the held correlation key and the held business id
    publishBlocked(HELD_CORRELATION_KEY, HELD_BUSINESS_ID);

    // then the correlation key takes precedence — the business-id gate is not even probed (M14)
    assertThat(blockedCount("correlation_key")).isEqualTo(1.0);
    assertThat(blockedCount("business_id")).isZero();
  }

  private void publishBlocked(final String correlationKey, final String businessId) {
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(correlationKey)
        .withBusinessId(businessId)
        .withTimeToLive(0L)
        .publish();
    // The correlate (and the metric) runs while the PUBLISH command is processed; awaiting the
    // second PUBLISHED record (holder + this one) guarantees the block has been recorded.
    RecordingExporter.messageRecords(MessageIntent.PUBLISHED)
        .withName(MESSAGE_NAME)
        .limit(2)
        .asList();
  }

  private double blockedCount(final String reason) {
    final var counter =
        engine.getMeterRegistry().find(BLOCKED_METRIC).tag("reason", reason).counter();
    return counter != null ? counter.count() : 0.0;
  }
}
