/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.mailbox.api.InboxMessage;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.NewInboxMessage;
import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import io.camunda.zeebe.mailbox.api.leadership.PartitionLease;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What the contract tests cannot show with one caller: nodes that use the database at the same time
 * get the outcome that the stores promise, because the database decides, not the callers.
 */
abstract class ConcurrentStoreAccessTest {

  private static final int NODES = 8;

  private final ExecutorService executor = Executors.newFixedThreadPool(NODES);
  private RdbmsMailboxStores stores;
  private int partition;

  protected abstract DataSource dataSource();

  @BeforeEach
  void setUp() {
    stores = RdbmsMailboxStores.create(dataSource());
    partition = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void shouldHandOutEachMessageToOneOfTheConcurrentClaimers() throws Exception {
    // given
    final var store = stores.mailboxStore();
    final var sender = "sender-" + UUID.randomUUID();
    for (int requestId = 1; requestId <= 50; requestId++) {
      store.insert(
          new NewInboxMessage(
              partition, MessageKind.CLIENT_REQUEST, sender, requestId, new byte[] {1}));
    }

    // when
    final var claims =
        concurrently(node -> store.claim(partition, "claimer-" + node, 10, Duration.ofMinutes(1)));

    // then
    final var claimed = new ArrayList<Long>();
    claims.forEach(
        messages -> messages.stream().map(InboxMessage::requestId).forEach(claimed::add));
    assertThat(claimed).doesNotHaveDuplicates();
  }

  @Test
  void shouldLetOneOfTheConcurrentClaimsOfTheFirstLeaseWin() throws Exception {
    // when
    final var claims =
        concurrently(
            node ->
                stores
                    .leaseStore()
                    .claim(partition, "node-" + node, "east", LeaseStore.NO_LEASE, 0));

    // then
    assertThat(claims).filteredOn(Optional::isPresent).hasSize(1);
  }

  @Test
  void shouldLetOneOfTheConcurrentTakeoversWin() throws Exception {
    // given
    final var leases = stores.leaseStore();
    final PartitionLease first =
        leases.claim(partition, "old-leader", "east", LeaseStore.NO_LEASE, 0).orElseThrow();

    // when
    final var claims =
        concurrently(
            node ->
                leases.claim(
                    partition, "node-" + node, "west", first.term(), first.renewalCounter()));

    // then
    assertThat(claims).filteredOn(Optional::isPresent).hasSize(1);
    assertThat(leases.findLease(partition))
        .hasValueSatisfying(l -> assertThat(l.term()).isEqualTo(2));
  }

  /** Runs the call on every node at once and returns what each returned. */
  private <T> List<T> concurrently(final IntFunction<T> call) throws Exception {
    final var start = new CountDownLatch(1);
    final var futures = new ArrayList<Future<T>>();
    for (int node = 0; node < NODES; node++) {
      final int id = node;
      final Callable<T> task =
          () -> {
            start.await();
            return call.apply(id);
          };
      futures.add(executor.submit(task));
    }
    start.countDown();
    final var results = new ArrayList<T>();
    for (final var future : futures) {
      results.add(future.get());
    }
    return results;
  }
}
