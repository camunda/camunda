/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.adminapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_MOCKS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.partitioning.PartitionAdminAccess;
import io.camunda.zeebe.protocol.impl.encoding.AdminRequest;
import io.camunda.zeebe.protocol.impl.encoding.AdminResponse;
import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.management.AdminRequestType;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Execution(ExecutionMode.CONCURRENT)
final class AdminApiRequestHandlerTest {

  private CompletableFuture<Either<ErrorResponse, AdminResponse>> handleRequest(
      final AdminRequest request, final AdminApiRequestHandler handler) {
    final var future = new CompletableFuture<Either<ErrorResponse, AdminResponse>>();
    final ServerOutput serverOutput = createServerOutput(future);
    final var requestBuffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(requestBuffer, 0);
    handler.onRequest(
        serverOutput,
        new PartitionId("raft-partition", request.getPartitionId()),
        0,
        requestBuffer,
        0,
        request.getLength());
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

  private static void assertErrorCode(
      final CompletableFuture<Either<ErrorResponse, AdminResponse>> response,
      final ErrorCode expectedErrorCode) {
    assertThat(response)
        .succeedsWithin(Duration.ofMinutes(1))
        .matches(Either::isLeft)
        .extracting(Either::getLeft)
        .extracting(ErrorResponse::getErrorCode)
        .isEqualTo(expectedErrorCode);
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  final class OtherRequest {
    @RegisterExtension
    final ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

    private final AdminApiRequestHandler handler;

    OtherRequest(
        @Mock final AtomixServerTransport transport,
        @Mock final PartitionAdminAccess adminAccess,
        @Mock(answer = RETURNS_MOCKS) final RaftPartition raftPartition) {
      handler = new AdminApiRequestHandler(transport, adminAccess, raftPartition);
    }

    @BeforeEach
    void installHandler() {
      scheduler.submitActor(handler);
      scheduler.workUntilDone();
    }

    @Test
    void shouldRejectRequestWithInvalidType() {
      // given
      final var request = new AdminRequest();
      request.setType(AdminRequestType.NULL_VAL);

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertErrorCode(responseFuture, ErrorCode.UNSUPPORTED_MESSAGE);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  final class PauseExportingRequest {
    @RegisterExtension
    final ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

    private final PartitionAdminAccess adminAccess;
    private final AdminApiRequestHandler handler;
    private final AdminRequest request;

    public PauseExportingRequest(
        @Mock final PartitionAdminAccess adminAccess,
        @Mock(answer = RETURNS_MOCKS) final RaftPartition raftPartition,
        @Mock final AtomixServerTransport transport) {
      this.adminAccess = adminAccess;
      final int partitionId = 1;
      when(adminAccess.forPartition(partitionId)).thenReturn(Optional.of(adminAccess));
      handler = new AdminApiRequestHandler(transport, adminAccess, raftPartition);

      request = new AdminRequest();
      request.setPartitionId(partitionId);
      request.setType(AdminRequestType.PAUSE_EXPORTING);
    }

    @BeforeEach
    void startHandler() {
      scheduler.submitActor(handler);
      scheduler.workUntilDone();
    }

    @Test
    void shouldPauseExportingForGivenPartition() {
      when(adminAccess.pauseExporting()).thenReturn(CompletableActorFuture.completed(null));

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertThat(responseFuture).succeedsWithin(Duration.ofMinutes(1)).matches(Either::isRight);
      verify(adminAccess).forPartition(request.getPartitionId());
      verify(adminAccess).pauseExporting();
    }

    @Test
    void shouldRespondWithFailureIfPausingFails() {
      // given
      when(adminAccess.pauseExporting())
          .thenReturn(
              CompletableActorFuture.completedExceptionally(
                  new RuntimeException("Exporting fails")));

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertErrorCode(responseFuture, ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void shouldRespondWithFailureIfPartitionNotFound() {
      // given
      request.setPartitionId(5);

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertErrorCode(responseFuture, ErrorCode.INTERNAL_ERROR);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  final class SoftPauseExportingRequest {
    @RegisterExtension
    final ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

    private final PartitionAdminAccess adminAccess;
    private final AdminApiRequestHandler handler;
    private final AdminRequest request;

    public SoftPauseExportingRequest(
        @Mock final PartitionAdminAccess adminAccess,
        @Mock(answer = RETURNS_MOCKS) final RaftPartition raftPartition,
        @Mock final AtomixServerTransport transport) {
      this.adminAccess = adminAccess;
      final int partitionId = 1;
      when(adminAccess.forPartition(partitionId)).thenReturn(Optional.of(adminAccess));
      handler = new AdminApiRequestHandler(transport, adminAccess, raftPartition);

      request = new AdminRequest();
      request.setPartitionId(partitionId);
      request.setType(AdminRequestType.SOFT_PAUSE_EXPORTING);
    }

    @BeforeEach
    void startHandler() {
      scheduler.submitActor(handler);
      scheduler.workUntilDone();
    }

    @Test
    void shouldSoftPauseExportingForGivenPartition() {
      when(adminAccess.softPauseExporting()).thenReturn(CompletableActorFuture.completed(null));

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertThat(responseFuture).succeedsWithin(Duration.ofMinutes(1)).matches(Either::isRight);
      verify(adminAccess).forPartition(request.getPartitionId());
      verify(adminAccess).softPauseExporting();
    }

    @Test
    void shouldRespondWithFailureIfPausingFails() {
      // given
      when(adminAccess.softPauseExporting())
          .thenReturn(
              CompletableActorFuture.completedExceptionally(
                  new RuntimeException("Exporting fails")));

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertErrorCode(responseFuture, ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void shouldRespondWithFailureIfPartitionNotFound() {
      // given
      request.setPartitionId(5);

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertErrorCode(responseFuture, ErrorCode.INTERNAL_ERROR);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  final class ResumeExportingRequest {
    @RegisterExtension
    final ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

    final AdminRequest request;
    private final PartitionAdminAccess adminAccess;
    private final AdminApiRequestHandler handler;

    public ResumeExportingRequest(
        @Mock final PartitionAdminAccess adminAccess,
        @Mock(answer = RETURNS_MOCKS) final RaftPartition raftPartition,
        @Mock final AtomixServerTransport transport) {
      this.adminAccess = adminAccess;
      final int partitionId = 1;
      when(adminAccess.forPartition(partitionId)).thenReturn(Optional.of(adminAccess));
      handler = new AdminApiRequestHandler(transport, adminAccess, raftPartition);

      request = new AdminRequest();
      request.setPartitionId(partitionId);
      request.setType(AdminRequestType.RESUME_EXPORTING);
    }

    @BeforeEach
    void startHandler() {
      scheduler.submitActor(handler);
      scheduler.workUntilDone();
    }

    @Test
    void shouldResumeExportingForGivenPartition() {
      when(adminAccess.resumeExporting()).thenReturn(CompletableActorFuture.completed(null));

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertThat(responseFuture).succeedsWithin(Duration.ofMinutes(1)).matches(Either::isRight);
      verify(adminAccess).forPartition(request.getPartitionId());
      verify(adminAccess).resumeExporting();
    }

    @Test
    void shouldRespondWithFailureIfPausingFails() {
      // given
      when(adminAccess.resumeExporting())
          .thenReturn(
              CompletableActorFuture.completedExceptionally(
                  new RuntimeException("Exporting fails")));

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertErrorCode(responseFuture, ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void shouldRespondWithFailureIfPartitionNotFound() {
      // given
      request.setPartitionId(5);

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertErrorCode(responseFuture, ErrorCode.INTERNAL_ERROR);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  final class StepdownRequest {
    @RegisterExtension
    final ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

    private final AdminApiRequestHandler handler;
    private final RaftPartition raftPartition;

    StepdownRequest(
        @Mock final AtomixServerTransport transport,
        @Mock final PartitionAdminAccess adminAccess,
        @Mock(answer = RETURNS_MOCKS) final RaftPartition raftPartition) {
      this.raftPartition = raftPartition;
      handler = new AdminApiRequestHandler(transport, adminAccess, raftPartition);
    }

    @BeforeEach
    void installHandler() {
      scheduler.submitActor(handler);
      scheduler.workUntilDone();
    }

    @Test
    void shouldInitiateStepdown() {
      // given
      when(raftPartition.getRole()).thenReturn(Role.LEADER);

      final var request = new AdminRequest();
      request.setType(AdminRequestType.STEP_DOWN_IF_NOT_PRIMARY);

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertThat(responseFuture).succeedsWithin(Duration.ofMinutes(1)).matches(Either::isRight);
    }

    @Test
    void shouldRejectRequestWhenNoPartitionsAreKnown() {
      // given

      final var request = new AdminRequest();
      request.setType(AdminRequestType.STEP_DOWN_IF_NOT_PRIMARY);

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertErrorCode(responseFuture, ErrorCode.PARTITION_LEADER_MISMATCH);
    }

    @Test
    void shouldRejectRequestWhenNotLeader() {
      // given
      when(raftPartition.getRole()).thenReturn(Role.FOLLOWER);

      final var request = new AdminRequest();
      request.setType(AdminRequestType.STEP_DOWN_IF_NOT_PRIMARY);

      // when
      final var responseFuture = handleRequest(request, handler);
      scheduler.workUntilDone();

      // then
      assertErrorCode(responseFuture, ErrorCode.PARTITION_LEADER_MISMATCH);
    }
  }
}
