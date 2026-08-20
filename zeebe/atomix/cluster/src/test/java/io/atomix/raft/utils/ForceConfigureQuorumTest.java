/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ForceConfigureQuorumTest {

  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final MemberId id2 = MemberId.from("2");
  private final Set<MemberId> members = Set.of(id0, id1, id2);

  @Test
  void shouldSucceedWhenAllAck() {
    // given
    final AtomicBoolean success = new AtomicBoolean();
    final ForceConfigureQuorum quorum = new ForceConfigureQuorum(success::set, members);

    // when
    members.forEach(quorum::succeed);

    // then
    assertThat(success).isTrue();
  }

  @Test
  void shouldFailWhenOneFails() {
    // given
    final AtomicBoolean success = new AtomicBoolean();
    final ForceConfigureQuorum quorum = new ForceConfigureQuorum(success::set, members);

    // when
    quorum.succeed(id0);
    quorum.succeed(id1);
    quorum.fail(id2);

    // then
    assertThat(success).isFalse();
  }

  @Test
  void shouldCallbackImmediatelyIfOneFailed() {
    // given
    final CompletableFuture<Boolean> quorumFuture = new CompletableFuture<>();
    final ForceConfigureQuorum quorum = new ForceConfigureQuorum(quorumFuture::complete, members);

    // when
    quorum.fail(id2);

    // then
    assertThat(quorumFuture.join()).isFalse();
  }
}
