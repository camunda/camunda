/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Direct applier-level tests for {@link MessageSubscriptionRejectedV2Applier}'s subscription
 * removal and correlation-lock release decisions, across both the non-stale (V1 parity) and
 * stale-generation branches. See <a
 * href="https://github.com/camunda/camunda/issues/61099">#61099</a>.
 */
@ExtendWith(ProcessingStateExtension.class)
final class MessageSubscriptionRejectedV2ApplierTest {

  private static final String BPMN_PROCESS_ID = "process";
  private static final String MESSAGE_NAME = "message";
  private static final long ELEMENT_INSTANCE_KEY = 10L;
  private static final long MESSAGE_KEY = 20L;
  private static final long K1_KEY = 30L;
  private static final long K2_KEY = 40L;

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableMessageState messageState;
  private MutableMessageSubscriptionState subscriptionState;
  private MessageSubscriptionRejectedV2Applier applier;

  @BeforeEach
  void setup() {
    messageState = processingState.getMessageState();
    subscriptionState = processingState.getMessageSubscriptionState();
    applier = new MessageSubscriptionRejectedV2Applier(messageState, subscriptionState);

    // the correlation-lock CF's key is a foreign key into the message CF
    messageState.put(
        MESSAGE_KEY,
        new MessageRecord()
            .setName(MESSAGE_NAME)
            .setCorrelationKey("correlationKey")
            .setTimeToLive(10_000L)
            .setDeadline(0L));
  }

  @Test
  void shouldNotReleaseLegacyLockWhenAliveReplacementStillReliesOnIt() {
    // given
    messageState.putMessageCorrelation(MESSAGE_KEY, bpmnProcessId());
    subscriptionState.put(K2_KEY, subscriptionRecord(K2_KEY, MESSAGE_KEY));

    // when
    applier.applyState(99L, rejectRecord(K1_KEY));

    // then
    assertThat(messageState.existMessageCorrelation(MESSAGE_KEY, bpmnProcessId())).isTrue();
    assertThat(subscriptionState.get(ELEMENT_INSTANCE_KEY, BufferUtil.wrapString(MESSAGE_NAME)))
        .as("the stale reject must not remove the live replacement K2")
        .isNotNull();
  }

  @Test
  void shouldReleaseLegacyLockWhenNoSubscriptionCurrentlyClaimsIt() {
    // given
    messageState.putMessageCorrelation(MESSAGE_KEY, bpmnProcessId());

    // when
    applier.applyState(99L, rejectRecord(K1_KEY));

    // then
    assertThat(messageState.existMessageCorrelation(MESSAGE_KEY, bpmnProcessId())).isFalse();
  }

  @Test
  void shouldReleaseLegacyLockWhenStoredSubscriptionShowsADifferentMessage() {
    // given
    messageState.putMessageCorrelation(MESSAGE_KEY, bpmnProcessId());
    subscriptionState.put(K2_KEY, subscriptionRecord(K2_KEY, MESSAGE_KEY + 1));

    // when
    applier.applyState(99L, rejectRecord(K1_KEY));

    // then
    assertThat(messageState.existMessageCorrelation(MESSAGE_KEY, bpmnProcessId())).isFalse();
    assertThat(subscriptionState.get(ELEMENT_INSTANCE_KEY, BufferUtil.wrapString(MESSAGE_NAME)))
        .isNotNull();
  }

  @Test
  void shouldRemoveSubscriptionAndReleaseLockWhenRejectIsNotStale() {
    // given
    messageState.putMessageCorrelation(MESSAGE_KEY, bpmnProcessId());
    subscriptionState.put(K1_KEY, subscriptionRecord(K1_KEY, MESSAGE_KEY));

    // when
    applier.applyState(99L, rejectRecord(K1_KEY));

    // then
    assertThat(subscriptionState.get(ELEMENT_INSTANCE_KEY, BufferUtil.wrapString(MESSAGE_NAME)))
        .isNull();
    assertThat(messageState.existMessageCorrelation(MESSAGE_KEY, bpmnProcessId())).isFalse();
  }

  private MessageSubscriptionRecord subscriptionRecord(
      final long subscriptionKey, final long messageKey) {
    return new MessageSubscriptionRecord()
        .setSubscriptionKey(subscriptionKey)
        .setProcessInstanceKey(1L)
        .setElementInstanceKey(ELEMENT_INSTANCE_KEY)
        .setMessageName(BufferUtil.wrapString(MESSAGE_NAME))
        .setBpmnProcessId(BufferUtil.wrapString(BPMN_PROCESS_ID))
        .setCorrelationKey(BufferUtil.wrapString("correlationKey"))
        .setMessageKey(messageKey)
        .setInterrupting(false)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private MessageSubscriptionRecord rejectRecord(final long subscriptionKey) {
    return subscriptionRecord(subscriptionKey, MESSAGE_KEY);
  }

  private org.agrona.DirectBuffer bpmnProcessId() {
    return BufferUtil.wrapString(BPMN_PROCESS_ID);
  }
}
