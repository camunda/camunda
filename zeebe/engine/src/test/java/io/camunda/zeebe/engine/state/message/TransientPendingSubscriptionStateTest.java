/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransientPendingSubscriptionStateTest {

  private TransientPendingSubscriptionState sut;

  @BeforeEach
  public void setUp() {
    sut = new TransientPendingSubscriptionState();
  }

  @Test
  public void shouldReturnNoEntriesByDefault() {
    // when
    final var actual = sut.entriesBefore(Long.MAX_VALUE);

    // then
    assertThat(actual).isEmpty();
  }

  @Test
  public void shouldReturnEntriesBeforeDeadline() {
    // when
    final var expected =
        new PendingSubscription(1, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    sut.add(expected, 500);
    sut.add(new PendingSubscription(2, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER), 2000);

    // when
    final var actual = sut.entriesBefore(1000);

    // then
    assertThat(actual).containsExactly(expected);
  }

  @Test
  public void shouldReturnEntriesOrderedBySentTime() {
    // when
    final var first = new PendingSubscription(1, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    final var second = new PendingSubscription(2, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    final var third = new PendingSubscription(3, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    sut.add(second, 600);
    sut.add(first, 500);
    sut.add(third, 700);

    // when
    final var actual = sut.entriesBefore(1000);

    // then
    assertThat(actual).containsExactly(first, second, third);
  }

  @Test
  public void shouldOverwriteExistingEntries() {
    // when
    final var subscription =
        new PendingSubscription(1, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    sut.add(subscription, 500);
    sut.add(subscription, 600);

    // when
    final var actual = sut.entriesBefore(1000);

    // then
    assertThat(actual).containsExactly(subscription);
  }

  @Test
  public void shouldAcceptEntriesWithTheSameSentTime() {
    // when
    sut.add(new PendingSubscription(1, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER), 500);
    sut.add(new PendingSubscription(2, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER), 500);

    // when
    final var actual = sut.entriesBefore(1000);

    // then
    assertThat(actual).hasSize(2);
  }

  @Test
  public void shouldReturnEntriesBasedOnUpdatedSentTime() {
    // when
    sut.add(new PendingSubscription(1, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER), 2000);
    sut.add(new PendingSubscription(2, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER), 3000);

    // when
    final var expected =
        new PendingSubscription(1, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    sut.update(expected, 500);
    final var actual = sut.entriesBefore(1000);

    // then
    assertThat(actual).containsExactly(expected);
  }

  @Test
  public void shouldNotReturnEntriesThatHaveBeenRemoved() {
    // when
    sut.add(new PendingSubscription(1, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER), 500);
    sut.add(new PendingSubscription(2, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER), 2000);

    // when
    sut.remove(new PendingSubscription(1, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER));
    final var actual = sut.entriesBefore(1000);

    // then
    assertThat(actual).isEmpty();
  }

  @Test
  public void shouldBeTolerantWhenRemovingEntriesThatDoNotExist() {
    // when + then
    assertThatNoException()
        .isThrownBy(
            () ->
                sut.remove(
                    new PendingSubscription(1, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER)));
  }

  @Test
  public void shouldBeTolerantWhenUpdatingEntriesThatDoNotExist() {
    // when + then
    assertThatNoException()
        .isThrownBy(
            () ->
                sut.update(
                    new PendingSubscription(1, "message", TenantOwned.DEFAULT_TENANT_IDENTIFIER),
                    500));
  }
}
