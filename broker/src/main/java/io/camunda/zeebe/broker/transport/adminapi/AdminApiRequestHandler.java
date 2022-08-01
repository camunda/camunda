/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.adminapi;

import io.atomix.primitive.partition.Partition;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.partitioning.PartitionAdminAccess;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.transport.ApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.protocol.management.AdminRequestType;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;

public class AdminApiRequestHandler extends ApiRequestHandler<ApiRequestReader, ApiResponseWriter> {
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
    partitionManager.getPartitionGroup().getPartitions().stream()
        .map(Partition::id)
        .forEach(partitionId -> transport.subscribe(partitionId.id(), RequestType.ADMIN, this));
  }

  @Override
  protected Either<ErrorResponseWriter, ApiResponseWriter> handle(
      final int partitionId,
      final long requestId,
      final ApiRequestReader requestReader,
      final ApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    if (requestReader.getMessageDecoder().type() == AdminRequestType.STEP_DOWN_IF_NOT_PRIMARY) {
      return stepDownIfNotPrimary(responseWriter, partitionId, errorWriter);
    }
    return unknownRequest(errorWriter, requestReader.getMessageDecoder().type());
  }

  private Either<ErrorResponseWriter, ApiResponseWriter> unknownRequest(
      final ErrorResponseWriter errorWriter, final AdminRequestType type) {
    errorWriter.unsupportedMessage(type, AdminRequestType.values());
    return Either.left(errorWriter);
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
