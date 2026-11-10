/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

final class LeaderPublisherTest {

  private final InMemoryMailboxStore store = new InMemoryMailboxStore();
  private final LeaderPublisher publisher =
      new LeaderPublisher(store, Runnable::run, 1, "eu_0", "eu");

  @Test
  void shouldRecordTheNodeAsLeader() {
    // when
    publisher.publish(3);

    // then
    assertThat(store.findLeader(1)).contains(new PartitionLeader(1, "eu_0", "eu", 3));
  }

  @Test
  void shouldRemoveTheRecordOfTheTerm() {
    // given
    publisher.publish(3);

    // when
    publisher.withdraw(3);

    // then
    assertThat(store.findLeader(1)).isEmpty();
  }

  @Test
  void shouldKeepTheRecordOfALaterTerm() {
    // given
    store.putLeader(new PartitionLeader(1, "us_0", "us", 4));

    // when
    publisher.withdraw(3);

    // then
    assertThat(store.findLeader(1)).isPresent();
  }

  @Test
  void shouldRunTheCallsInTheOrderTheyWereMade() {
    // given - the executor runs the tasks later, one by one
    final var queued = new ArrayDeque<Runnable>();
    final var queueing = new LeaderPublisher(store, queued::add, 1, "eu_0", "eu");

    // when
    queueing.publish(3);
    queueing.withdraw(3);
    while (!queued.isEmpty()) {
      queued.poll().run();
    }

    // then
    assertThat(store.findLeader(1)).isEmpty();
  }

  @Test
  void shouldNotFailWhenTheStoreFails() {
    // given
    final var failing = mock(MailboxStore.class);
    doThrow(new MailboxStoreException("down", new RuntimeException()))
        .when(failing)
        .putLeader(any());
    final var failingPublisher = new LeaderPublisher(failing, Runnable::run, 1, "eu_0", "eu");

    // when / then
    assertThatCode(() -> failingPublisher.publish(3)).doesNotThrowAnyException();
  }
}
