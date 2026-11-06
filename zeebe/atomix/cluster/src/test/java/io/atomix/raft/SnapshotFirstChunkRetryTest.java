/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.snapshot.impl.SnapshotChunkImpl;
import io.camunda.zeebe.snapshots.impl.SnapshotChunkId;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SnapshotFirstChunkRetryTest {
  private static final MemberId LEADER = MemberId.from("0");
  private static final MemberId JOINING_MEMBER = MemberId.from("1");
  private static final int CHUNK_SIZE = 4;

  @TempDir private Path directory;
  private ControllableRaftContexts raft;

  @BeforeEach
  void setup() throws Exception {
    raft = new ControllableRaftContexts(2, config -> config.setSnapshotChunkSize(CHUNK_SIZE));
    raft.setup(directory, new Random(1), List.of(0));
  }

  @AfterEach
  void shutdown() throws IOException {
    raft.shutdown();
  }

  @Test
  void shouldRetryFirstSplitSnapshotChunkFromItsOriginalOffsetAfterTimeout() throws IOException {
    // given
    raft.tickElectionTimeout(LEADER);
    raft.runUntilDone(LEADER);
    assertThat(context(LEADER).getRole()).isEqualTo(Role.LEADER);
    raft.takeBootstrapSnapshot(LEADER);
    raft.runUntilDone(LEADER);
    final var snapshot = context(LEADER).getCurrentSnapshot();
    assertThat(snapshot).isNotNull();

    final var join = raft.join(JOINING_MEMBER, List.of(LEADER));
    final List<CapturedChunk> attempts = new ArrayList<>();
    raft.getServerProtocol(JOINING_MEMBER)
        .interceptInstallRequests(
            handler ->
                request -> {
                  if (attempts.size() < 2) {
                    attempts.add(capture(request));
                    if (attempts.size() == 1) {
                      // Drop the request before it reaches the follower; the sender will time out.
                      return new CompletableFuture<>();
                    }
                  }
                  return handler.apply(request);
                });

    for (int i = 0; i < 50 && attempts.isEmpty(); i++) {
      pumpReplication();
    }
    assertThat(attempts).hasSize(1);
    final var first = attempts.getFirst();
    assertThat(first.initial()).isTrue();
    assertThat(first.complete()).isFalse();
    assertThat(first.offset()).isZero();
    assertThat(first.content()).hasSize(CHUNK_SIZE);
    assertThat(first.fileSize()).isGreaterThan(CHUNK_SIZE);
    assertThat(new SnapshotChunkId(ByteBuffer.wrap(first.chunkId())).offset()).isZero();
    assertThat(first.nextChunkId()).isNotNull();
    final var nextId = new SnapshotChunkId(ByteBuffer.wrap(first.nextChunkId()));
    assertThat(nextId.fileName()).isEqualTo(first.fileName());
    assertThat(nextId.offset()).isEqualTo(CHUNK_SIZE);
    assertThat(Files.readAllBytes(snapshot.getPath().resolve(first.fileName())))
        .startsWith(first.content());

    // when
    raft.tick(LEADER, Duration.ofSeconds(5));
    raft.runUntilDone(LEADER);
    for (int i = 0; i < 50 && attempts.size() < 2; i++) {
      pumpReplication();
    }

    // then
    assertThat(attempts).hasSize(2);
    final var retry = attempts.get(1);
    assertThat(retry.initial()).isTrue();
    assertThat(retry.complete()).isFalse();
    assertThat(retry.chunkId()).isEqualTo(first.chunkId());
    assertThat(retry.nextChunkId()).isEqualTo(first.nextChunkId());
    assertThat(retry.offset()).isEqualTo(first.offset());
    assertThat(retry.fileName()).isEqualTo(first.fileName());
    assertThat(retry.content()).isEqualTo(first.content());
    assertThat(retry.data()).isEqualTo(first.data());

    for (int i = 0; i < 200; i++) {
      if (context(JOINING_MEMBER).getCurrentSnapshotIndex() == snapshot.getIndex()
          && join.isDone()) {
        break;
      }
      deliverAll(JOINING_MEMBER);
      deliverAll(LEADER);
      if (i % 10 == 9) {
        raft.tickHeartbeatTimeout(LEADER);
      }
    }
    assertThat(join).isCompletedWithValue(null);
    assertThat(context(JOINING_MEMBER).getCurrentSnapshotIndex()).isEqualTo(snapshot.getIndex());
    final var receivedSnapshot = context(JOINING_MEMBER).getCurrentSnapshot();
    assertThat(receivedSnapshot.getId()).isEqualTo(snapshot.getId());
    assertThat(Files.readAllBytes(receivedSnapshot.getPath().resolve(first.fileName())))
        .isEqualTo(Files.readAllBytes(snapshot.getPath().resolve(first.fileName())));
  }

  private void pumpReplication() {
    raft.tickHeartbeatTimeout(LEADER);
    deliverAll(JOINING_MEMBER);
    deliverAll(LEADER);
  }

  private void deliverAll(final MemberId member) {
    raft.getServerProtocol(member).receiveAll();
    raft.runUntilDone(member);
  }

  private RaftContext context(final MemberId member) {
    return raft.getRaftContext(member);
  }

  private static CapturedChunk capture(final InstallRequest request) {
    final var chunk = new SnapshotChunkImpl();
    chunk.wrap(new UnsafeBuffer(request.data().duplicate()));
    return new CapturedChunk(
        copy(request.chunkId()),
        request.nextChunkId() == null ? null : copy(request.nextChunkId()),
        copy(request.data()),
        chunk.getChunkName(),
        chunk.getContent(),
        chunk.getFileBlockPosition(),
        chunk.getTotalFileSize(),
        request.isInitial(),
        request.complete());
  }

  private static byte[] copy(final ByteBuffer buffer) {
    final var view = buffer.duplicate();
    final var bytes = new byte[view.remaining()];
    view.get(bytes);
    return bytes;
  }

  private record CapturedChunk(
      byte[] chunkId,
      byte[] nextChunkId,
      byte[] data,
      String fileName,
      byte[] content,
      long offset,
      long fileSize,
      boolean initial,
      boolean complete) {}
}
