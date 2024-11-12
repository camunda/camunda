/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import io.atomix.raft.impl.RaftContext.State;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public class RaftSingleNodeTest {
  @Rule public final RaftRule rule = RaftRule.withBootstrappedNodes(1);

  @Test
  public void singleNodeClusterShouldBecomeReadyOnRestart() throws Exception {
    final var server = rule.getServers().iterator().next();
    final var name = server.name();
    Awaitility.await("Node should become ready")
        .until(() -> server.getContext().getState() == State.READY);
    rule.shutdownLeader();
    rule.joinCluster(name);

    Awaitility.await("Node should become ready")
        .until(() -> server.getContext().getState() == State.READY);
  }
}
