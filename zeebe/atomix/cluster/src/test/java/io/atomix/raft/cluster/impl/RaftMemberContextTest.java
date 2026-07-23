/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.cluster.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember.Type;
import io.camunda.zeebe.snapshots.SnapshotChunkReader;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class RaftMemberContextTest {

  @Test
  void shouldCloseSnapshotChunkReaderOnClose() {
    // given
    final var context = newContext();
    final var snapshotChunkReader = mock(SnapshotChunkReader.class);
    context.setSnapshotChunkReader(snapshotChunkReader);

    // when
    context.close();

    // then
    verify(snapshotChunkReader).close();
  }

  private RaftMemberContext newContext() {
    final var member = new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.now());
    return new RaftMemberContext(member, mock(RaftClusterContext.class), 1);
  }
}
