/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the contract of the optional {@code businessId} field on the three message records carrying
 * it: the default representation is an empty string (not null), and {@code wrap} both copies the
 * field from a source that has one and resets it when the source has none. All three invariants are
 * load-bearing for the asymmetric "no businessId on the message ⇒ no constraint" semantics — a null
 * leak, a missing copy, or a stale value carried over from a pooled record would silently change
 * correlation behavior.
 */
final class MessageBusinessIdSerializationTest {

  @Test
  void shouldDefaultMessageRecordBusinessIdToEmptyString() {
    // given
    final var record = new MessageRecord();

    // then
    assertThat(record.getBusinessId()).isEmpty();
  }

  @Test
  void shouldDefaultMessageSubscriptionRecordBusinessIdToEmptyString() {
    // given
    final var record = new MessageSubscriptionRecord();

    // then
    assertThat(record.getBusinessId()).isEmpty();
  }

  @Test
  void shouldDefaultProcessMessageSubscriptionRecordBusinessIdToEmptyString() {
    // given
    final var record = new ProcessMessageSubscriptionRecord();

    // then
    assertThat(record.getBusinessId()).isEmpty();
  }

  @Test
  void shouldCopyMessageRecordBusinessIdWhenWrappingRecordWithBusinessId() {
    // given
    final var source =
        new MessageRecord()
            .setName("msg")
            .setCorrelationKey("k")
            .setTimeToLive(0L)
            .setBusinessId("biz-42");
    final var target = new MessageRecord();

    // when
    target.wrap(source);

    // then
    assertThat(target.getBusinessId()).isEqualTo("biz-42");
  }

  @Test
  void shouldCopyMessageSubscriptionRecordBusinessIdWhenWrappingRecordWithBusinessId() {
    // given
    final var source =
        new MessageSubscriptionRecord()
            .setProcessInstanceKey(1L)
            .setElementInstanceKey(2L)
            .setBusinessId("biz-42");
    final var target = new MessageSubscriptionRecord();

    // when
    target.wrap(source);

    // then
    assertThat(target.getBusinessId()).isEqualTo("biz-42");
  }

  @Test
  void shouldCopyProcessMessageSubscriptionRecordBusinessIdWhenWrappingRecordWithBusinessId() {
    // given
    final var source =
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(1)
            .setProcessInstanceKey(2L)
            .setElementInstanceKey(3L)
            .setBusinessId("biz-42");
    final var target = new ProcessMessageSubscriptionRecord();

    // when
    target.wrap(source);

    // then
    assertThat(target.getBusinessId()).isEqualTo("biz-42");
  }

  @Test
  void shouldResetMessageRecordBusinessIdWhenWrappingRecordWithoutBusinessId() {
    // given
    final var source = new MessageRecord().setName("msg").setCorrelationKey("k").setTimeToLive(0L);
    final var target = new MessageRecord().setBusinessId("biz-from-previous-use");

    // when
    target.wrap(source);

    // then
    assertThat(target.getBusinessId()).isEmpty();
  }

  @Test
  void shouldResetMessageSubscriptionRecordBusinessIdWhenWrappingRecordWithoutBusinessId() {
    // given
    final var source =
        new MessageSubscriptionRecord().setProcessInstanceKey(1L).setElementInstanceKey(2L);
    final var target = new MessageSubscriptionRecord().setBusinessId("biz-from-previous-use");

    // when
    target.wrap(source);

    // then
    assertThat(target.getBusinessId()).isEmpty();
  }

  @Test
  void shouldResetProcessMessageSubscriptionRecordBusinessIdWhenWrappingRecordWithoutBusinessId() {
    // given
    final var source =
        new ProcessMessageSubscriptionRecord()
            .setSubscriptionPartitionId(1)
            .setProcessInstanceKey(2L)
            .setElementInstanceKey(3L);
    final var target =
        new ProcessMessageSubscriptionRecord().setBusinessId("biz-from-previous-use");

    // when
    target.wrap(source);

    // then
    assertThat(target.getBusinessId()).isEmpty();
  }
}
