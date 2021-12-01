/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.adminapi;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.transport.ApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.protocol.record.AdminRequestType;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;

public class AdminApiRequestHandler extends ApiRequestHandler<ApiRequestReader, ApiResponseWriter> {
  private RaftPartitionGroup partitionGroup;
  private final AtomixServerTransport transport;

  public AdminApiRequestHandler(final AtomixServerTransport transport) {
    super(new ApiRequestReader(), new ApiResponseWriter());
    this.transport = transport;
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
    if (partitionGroup == null) {
      errorWriter.partitionLeaderMismatch(partitionId);
      return Either.left(errorWriter);
    }
    final var partition = partitionGroup.getPartition(partitionId);
    partition.stepDownIfNotPrimary();

    return Either.right(responseWriter);
  }

  public void injectPartitionManager(final PartitionManagerImpl partitionManager) {
    partitionGroup = (RaftPartitionGroup) partitionManager.getPartitionGroup();
    partitionGroup.getPartitionIds().stream()
        .map(PartitionId::id)
        .forEach(partitionId -> transport.subscribe(partitionId, RequestType.ADMIN, this));
  }
}
