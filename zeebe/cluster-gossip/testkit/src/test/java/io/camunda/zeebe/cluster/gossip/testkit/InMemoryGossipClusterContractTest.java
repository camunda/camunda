/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.testkit;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.cluster.gossip.ClusterGossiper;
import io.camunda.zeebe.cluster.gossip.testkit.contract.ClusterGossiperContractTest;
import io.camunda.zeebe.cluster.gossip.testkit.contract.GossipTestCluster;

/** Runs the contract tests against the in-memory cluster. */
final class InMemoryGossipClusterContractTest extends ClusterGossiperContractTest {

  @Override
  protected GossipTestCluster startCluster(final int size) {
    return new InMemoryTestCluster(size);
  }

  /** Moves state by syncing the in-memory cluster before assertions. */
  private static final class InMemoryTestCluster implements GossipTestCluster {

    private static final int MAX_SYNCS = 3;

    private final InMemoryGossipCluster cluster = new InMemoryGossipCluster();

    private InMemoryTestCluster(final int size) {
      for (int i = 0; i < size; i++) {
        cluster.addMember(memberId(i));
      }
    }

    @Override
    public ClusterGossiper gossiper(final int index) {
      return cluster.member(memberId(index));
    }

    @Override
    public MemberId memberId(final int index) {
      return MemberId.from(String.valueOf(index));
    }

    @Override
    public void stop(final int index) {
      cluster.removeMember(memberId(index));
    }

    @Override
    public ClusterGossiper restart(final int index) {
      return cluster.restart(memberId(index));
    }

    /**
     * Syncs and asserts, a few times over: the first read of a key registers it, and values of a
     * custom-merge key that arrived before that are only delivered by the next sync (ADR 0001,
     * D14).
     */
    @Override
    public void awaitAsserted(final Runnable assertion) {
      for (int attempt = 1; ; attempt++) {
        cluster.sync();
        try {
          assertion.run();
          return;
        } catch (final AssertionError e) {
          if (attempt == MAX_SYNCS) {
            throw e;
          }
        }
      }
    }

    @Override
    public void close() {}
  }
}
