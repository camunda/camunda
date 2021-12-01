/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.adminapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.protocol.impl.encoding.AdminRequest;
import io.camunda.zeebe.protocol.impl.encoding.AdminResponse;
import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.record.AdminRequestType;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AdminApiRequestHandlerTest {
  @Rule public final ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();
  final AtomixServerTransport transport = mock(AtomixServerTransport.class);
  AdminApiRequestHandler handler;

  @Before
  public void setup() {
    final var partitionManager = mock(PartitionManagerImpl.class);
    final var partitionGroup = mock(RaftPartitionGroup.class);
    final var raftPartition = mock(RaftPartition.class);
    when(partitionManager.getPartitionGroup()).thenReturn(partitionGroup);
    when(partitionGroup.name()).thenReturn("test");
    when(raftPartition.stepDownIfNotPrimary()).thenReturn(CompletableFuture.completedFuture(null));
    when(partitionGroup.getPartition(anyInt())).thenReturn(raftPartition);
    when(partitionManager.getPartitionGroup()).thenReturn(partitionGroup);
    handler = new AdminApiRequestHandler(transport);
    scheduler.submitActor(handler);
    handler.injectPartitionManager(partitionManager);
    scheduler.workUntilDone();
  }

  @Test
  public void shouldRejectRequestWithInvalidType() {
    // given
    final var request = new AdminRequest();
    request.setType(AdminRequestType.NULL_VAL);

    // when
    final var responseFuture = handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft)
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode)
        .isEqualTo(ErrorCode.UNSUPPORTED_MESSAGE);
  }

  @Test
  public void shouldInitiateStepdown() {
    // given
    final var request = new AdminRequest();
    request.setType(AdminRequestType.STEP_DOWN_IF_NOT_PRIMARY);

    // when
    final var responseFuture = handleRequest(request);

    // then
    assertThat(responseFuture).succeedsWithin(Duration.ofMinutes(1)).matches(Either::isRight);
  }

  @Test
  public void shouldRejectRequestWhenPartitionsAreNotStarted() {
    // given
    final var partitionManager = mock(PartitionManagerImpl.class);
    final var partitionGroup = mock(RaftPartitionGroup.class);
    when(partitionGroup.getPartitionIds()).thenReturn(List.of());
    when(partitionManager.getPartitionGroup()).thenReturn(partitionGroup);
    handler.injectPartitionManager(partitionManager);

    final var request = new AdminRequest();
    request.setType(AdminRequestType.STEP_DOWN_IF_NOT_PRIMARY);

    // when
    final var responseFuture = handleRequest(request);

    // then
    assertThat(responseFuture)
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft)
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode)
        .isEqualTo(ErrorCode.INTERNAL_ERROR); // no partitions -> no handler subscribed
  }

  private CompletableFuture<Either<ErrorResponse, AdminResponse>> handleRequest(
      final BufferWriter request) {
    final var future = new CompletableFuture<Either<ErrorResponse, AdminResponse>>();
    final ServerOutput serverOutput = createServerOutput(future);
    final var requestBuffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(requestBuffer, 0);
    handler.onRequest(serverOutput, 0, 0, requestBuffer, 0, request.getLength());
    scheduler.workUntilDone();
    return future;
  }

  private ServerOutput createServerOutput(
      final CompletableFuture<Either<ErrorResponse, AdminResponse>> future) {
    return serverResponse -> {
      final var buffer = new ExpandableArrayBuffer();
      serverResponse.write(buffer, 0);

      final var error = new ErrorResponse();
      if (error.tryWrap(buffer)) {
        error.wrap(buffer, 0, serverResponse.getLength());
        future.complete(Either.left(error));
        return;
      }

      final var response = new AdminResponse();
      try {
        response.wrap(buffer, 0, serverResponse.getLength());
        future.complete(Either.right(response));
      } catch (final Exception e) {
        future.completeExceptionally(e);
      }
    };
  }
}
