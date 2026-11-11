/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.mailbox.leadership.InMemoryLeaseStore;
import io.camunda.zeebe.mailbox.leadership.LeaderElector;
import io.camunda.zeebe.mailbox.leadership.LeaseConfig;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** One node with two partitions. The test runs the tasks of the elector one by one. */
final class LeaseLeadershipsTest {

  private final Queue<Runnable> tasks = new ArrayDeque<>();
  private final InMemoryLeaseStore store = new InMemoryLeaseStore();
  private final LeaseLeaderships leaderships =
      new LeaseLeaderships(
          listener ->
              new LeaderElector(
                  store,
                  LeaseConfig.of(Duration.ofSeconds(9)),
                  "e1",
                  "east",
                  Set.of(1, 2),
                  () -> List.of("e1"),
                  listener,
                  (task, delay) -> tasks.add(task),
                  System::nanoTime));

  @AfterEach
  void close() {
    leaderships.close();
  }

  @Test
  void shouldTellThePartitionThatItLeads() {
    // given
    final var roles = new CopyOnWriteArrayList<String>();
    leaderships.forPartition(1).addListener((role, term) -> roles.add(role + "@" + term));

    // when
    leaderships.start();
    tasks.poll().run();

    // then
    assertThat(roles).containsExactly("INACTIVE@0", "LEADER@1");
    assertThat(leaderships.forPartition(1).role()).isEqualTo(Role.LEADER);
    assertThat(leaderships.forPartition(1).term()).isEqualTo(1);
  }

  @Test
  void shouldTellALateListenerTheCurrentRole() {
    // given
    leaderships.start();
    tasks.poll().run();

    // when
    final var roles = new CopyOnWriteArrayList<String>();
    leaderships.forPartition(2).addListener((role, term) -> roles.add(role + "@" + term));

    // then
    assertThat(roles).containsExactly("LEADER@1");
  }

  @Test
  void shouldBecomeAStandbyAfterAStepDown() {
    // given
    leaderships.start();
    tasks.poll().run();
    final var roles = new CopyOnWriteArrayList<String>();
    final var leadership = leaderships.forPartition(1);
    leadership.addListener((role, term) -> roles.add(role + "@" + term));

    // when
    final var stepDown = leadership.stepDown();
    runUntil(stepDown::isDone);

    // then
    assertThat(stepDown).isCompleted();
    assertThat(leadership.role()).isEqualTo(Role.INACTIVE);
    assertThat(roles).containsExactly("LEADER@1", "INACTIVE@1");
    assertThat(store.findLease(1).orElseThrow().released()).isTrue();
    assertThat(leaderships.forPartition(2).role()).isEqualTo(Role.LEADER);
  }

  @Test
  void shouldHandOutOneViewPerPartition() {
    // then
    assertThat(leaderships.forPartition(1)).isSameAs(leaderships.forPartition(1));
    assertThat(leaderships.forPartition(1)).isNotSameAs(leaderships.forPartition(2));
  }

  private void runUntil(final BooleanSupplier done) {
    while (!done.getAsBoolean() && !tasks.isEmpty()) {
      tasks.poll().run();
    }
  }
}
