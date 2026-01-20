/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.adminapi;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.partitioning.PartitionAdminAccess;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.logstreams.impl.flowcontrol.LimitSerializer;
import io.camunda.zeebe.protocol.management.AdminRequestType;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;
import java.io.IOException;

public class AdminApiRequestHandler
    extends AsyncApiRequestHandler<ApiRequestReader, ApiResponseWriter> {
  private final AtomixServerTransport transport;
  private final PartitionAdminAccess adminAccess;
  private final RaftPartition raftPartition;

  public AdminApiRequestHandler(
      final AtomixServerTransport transport,
      final PartitionAdminAccess adminAccess,
      final RaftPartition raftPartition) {
    super(ApiRequestReader::new, ApiResponseWriter::new);
    this.transport = transport;
    this.adminAccess = adminAccess;

    this.raftPartition = raftPartition;
  }

  @Override
  protected void onActorStarting() {
    transport.subscribe(raftPartition.id().id(), RequestType.ADMIN, this);
  }

  @Override
  protected void onActorClosing() {
    transport.unsubscribe(raftPartition.id().id(), RequestType.ADMIN);
  }

  @Override
  protected ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> handleAsync(
      final PartitionId partitionId,
      final long requestId,
      final ApiRequestReader requestReader,
      final ApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    // TODO: Use full partition id, including the group
    return switch (requestReader.getMessageDecoder().type()) {
      case STEP_DOWN_IF_NOT_PRIMARY ->
          CompletableActorFuture.completed(
              stepDownIfNotPrimary(responseWriter, partitionId, errorWriter));
      case PAUSE_EXPORTING -> pauseExporting(responseWriter, partitionId.id(), errorWriter);
      case SOFT_PAUSE_EXPORTING ->
          softPauseExporting(responseWriter, partitionId.id(), errorWriter);
      case RESUME_EXPORTING -> resumeExporting(responseWriter, partitionId.id(), errorWriter);
      case BAN_INSTANCE ->
          banInstance(requestReader, responseWriter, partitionId.id(), errorWriter);
      case GET_FLOW_CONTROL -> getFlowControl(responseWriter, errorWriter);
      case SET_FLOW_CONTROL -> setFlowControl(requestReader, responseWriter, errorWriter);
      default -> unknownRequest(errorWriter, requestReader.getMessageDecoder().type());
    };
  }

  private ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> setFlowControl(
      final ApiRequestReader requestReader,
      final ApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> result = actor.createFuture();

    final String payload = requestReader.payload();
    final FlowControlCfg flowControlCfg;

    try {
      flowControlCfg = FlowControlCfg.deserialize(payload);
    } catch (final IOException e) {
      LOG.error("Failed to parse the flow control configuration: ", e);
      result.complete(
          Either.left(
              errorWriter.internalError(
                  "Failed to parse the flow control configuration: %s".formatted(e.getMessage()))));
      return result;
    }

    adminAccess
        .configureFlowControl(flowControlCfg)
        .onComplete(
            (r, t) -> {
              if (t == null) {
                result.complete(Either.right(responseWriter));
              } else {
                LOG.error("Failed to set the flow control configuration.", t);
                result.complete(
                    Either.left(
                        errorWriter.internalError(
                            "Failed to set the flow control configuration: %s"
                                .formatted(t.getMessage()))));
              }
            });
    return result;
  }

  private ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> getFlowControl(
      final ApiResponseWriter responseWriter, final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> result = actor.createFuture();
    adminAccess
        .getFlowControlConfiguration()
        .onComplete(
            (r, t) -> {
              if (t == null) {
                responseWriter.setPayload(LimitSerializer.serialize(r));
                result.complete(Either.right(responseWriter));

              } else {
                LOG.error("Failed to get the flow control configuration.", t);
                result.complete(
                    Either.left(
                        errorWriter.internalError(
                            "Failed to get the flow control configuration.")));
              }
            });

    return result;
  }

  private ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> banInstance(
      final ApiRequestReader requestReader,
      final ApiResponseWriter responseWriter,
      final int partitionId,
      final ErrorResponseWriter errorWriter) {
    final long key = requestReader.key();

    final ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> result = actor.createFuture();
    adminAccess
        .banInstance(requestReader.key())
        .onComplete(
            (r, t) -> {
              if (t == null) {
                result.complete(Either.right(responseWriter));
              } else {
                LOG.error("Failed to ban instance {} on partition {}", key, partitionId, t);
                result.complete(
                    Either.left(
                        errorWriter.internalError(
                            "Failed to ban instance %s, on partition %s", key, partitionId)));
              }
            });

    return result;
  }

  private ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> unknownRequest(
      final ErrorResponseWriter errorWriter, final AdminRequestType type) {
    errorWriter.unsupportedMessage(type, AdminRequestType.values());
    return CompletableActorFuture.completed(Either.left(errorWriter));
  }

  private ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> pauseExporting(
      final ApiResponseWriter responseWriter,
      final int partitionId,
      final ErrorResponseWriter errorWriter) {
    final var partitionAdminAccess = adminAccess.forPartition(partitionId);
    if (partitionAdminAccess.isEmpty()) {
      return CompletableActorFuture.completed(
          Either.left(
              errorWriter.internalError(
                  "Partition %s failed to pause exporting. Could not find the partition.",
                  partitionId)));
    }

    final ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> result = actor.createFuture();
    partitionAdminAccess
        .orElseThrow()
        .pauseExporting()
        .onComplete(
            (r, t) -> {
              if (t == null) {
                result.complete(Either.right(responseWriter));
              } else {
                LOG.error("Failed to pause exporting on partition {}", partitionId, t);
                result.complete(
                    Either.left(
                        errorWriter.internalError(
                            "Partition %s failed to pause exporting", partitionId)));
              }
            });

    return result;
  }

  private ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> softPauseExporting(
      final ApiResponseWriter responseWriter,
      final int partitionId,
      final ErrorResponseWriter errorWriter) {
    final var partitionAdminAccess = adminAccess.forPartition(partitionId);
    if (partitionAdminAccess.isEmpty()) {
      return CompletableActorFuture.completed(
          Either.left(
              errorWriter.internalError(
                  "Partition %s failed to soft pause exporting. Could not find the partition.",
                  partitionId)));
    }

    final ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> result = actor.createFuture();
    partitionAdminAccess
        .orElseThrow()
        .softPauseExporting()
        .onComplete(
            (r, t) -> {
              if (t == null) {
                result.complete(Either.right(responseWriter));
              } else {
                LOG.error("Failed to soft pause exporting on partition {}", partitionId, t);
                result.complete(
                    Either.left(
                        errorWriter.internalError(
                            "Partition %s failed to soft pause exporting", partitionId)));
              }
            });

    return result;
  }

  private ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> resumeExporting(
      final ApiResponseWriter responseWriter,
      final int partitionId,
      final ErrorResponseWriter errorWriter) {
    final var partitionAdminAccess = adminAccess.forPartition(partitionId);
    if (partitionAdminAccess.isEmpty()) {
      return CompletableActorFuture.completed(
          Either.left(
              errorWriter.internalError(
                  "Partition %s failed to resume exporting. Could not find the partition.",
                  partitionId)));
    }

    final ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> result = actor.createFuture();
    partitionAdminAccess
        .orElseThrow()
        .resumeExporting()
        .onComplete(
            (r, t) -> {
              if (t == null) {
                result.complete(Either.right(responseWriter));
              } else {
                LOG.error("Failed to resume exporting on partition {}", partitionId, t);
                result.complete(
                    Either.left(
                        errorWriter.internalError(
                            "Partition %s failed to resume exporting", partitionId)));
              }
            });

    return result;
  }

  private Either<ErrorResponseWriter, ApiResponseWriter> stepDownIfNotPrimary(
      final ApiResponseWriter responseWriter,
      final PartitionId partitionId,
      final ErrorResponseWriter errorWriter) {
    if (raftPartition.getRole() == Role.LEADER) {
      raftPartition.stepDownIfNotPrimary();
    } else {
      errorWriter.partitionLeaderMismatch(partitionId);
      return Either.left(errorWriter);
    }

    return Either.right(responseWriter);
  }
}
