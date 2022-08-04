/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.adminapi;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.partitioning.PartitionAdminAccess;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.protocol.management.AdminRequestType;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;

public class AdminApiRequestHandler
    extends AsyncApiRequestHandler<ApiRequestReader, ApiResponseWriter> {
  private final PartitionAdminAccess adminAccess;
  private final PartitionManagerImpl partitionManager;
  private final AtomixServerTransport transport;

  public AdminApiRequestHandler(
      final AtomixServerTransport transport, final PartitionManagerImpl partitionManager) {
    super(new ApiRequestReader(), new ApiResponseWriter());
    this.transport = transport;
    this.partitionManager = partitionManager;
    adminAccess = partitionManager.createAdminAccess(this);
  }

  @Override
  protected void onActorStarting() {
    partitionManager.getPartitions().stream()
        .map(ZeebePartition::getPartitionId)
        .forEach(partitionId -> transport.subscribe(partitionId, RequestType.ADMIN, this));
  }

  @Override
  protected ActorFuture<Either<ErrorResponseWriter, ApiResponseWriter>> handleAsync(
      final int partitionId,
      final long requestId,
      final ApiRequestReader requestReader,
      final ApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    return switch (requestReader.getMessageDecoder().type()) {
      case STEP_DOWN_IF_NOT_PRIMARY -> CompletableActorFuture.completed(
          stepDownIfNotPrimary(responseWriter, partitionId, errorWriter));
      case PAUSE_EXPORTING -> pauseExporting(responseWriter, partitionId, errorWriter);
      default -> unknownRequest(errorWriter, requestReader.getMessageDecoder().type());
    };
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

  private Either<ErrorResponseWriter, ApiResponseWriter> stepDownIfNotPrimary(
      final ApiResponseWriter responseWriter,
      final int partitionId,
      final ErrorResponseWriter errorWriter) {
    final var partition = partitionManager.getPartitionGroup().getPartition(partitionId);
    if (partition instanceof RaftPartition raftPartition) {
      if (raftPartition.getRole() == Role.LEADER) {
        raftPartition.stepDownIfNotPrimary();
      } else {
        errorWriter.partitionLeaderMismatch(partitionId);
        return Either.left(errorWriter);
      }
    } else if (partition == null) {
      errorWriter.partitionLeaderMismatch(partitionId);
      return Either.left(errorWriter);
    } else {
      throw new IllegalStateException(
          "Expected a raft partition, got %s".formatted(partition.getClass().getName()));
    }

    return Either.right(responseWriter);
  }
}
