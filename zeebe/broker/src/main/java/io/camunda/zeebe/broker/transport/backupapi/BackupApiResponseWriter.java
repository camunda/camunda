/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.ResponseWriter;
import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.impl.ServerResponseImpl;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import org.agrona.MutableDirectBuffer;

public final class BackupApiResponseWriter implements ResponseWriter {
  private final ServerResponseImpl response = new ServerResponseImpl();

  private boolean hasResponse = true;

  private BiConsumer<MutableDirectBuffer, Integer> responseWriter;
  private IntSupplier lengthSupplier;

  BackupApiResponseWriter withStatus(final BackupStatusResponse response) {
    responseWriter = response::write;
    lengthSupplier = response::getLength;
    return this;
  }

  BackupApiResponseWriter withBackupList(final BackupListResponse response) {
    responseWriter = response::write;
    lengthSupplier = response::getLength;
    return this;
  }

  BackupApiResponseWriter noResponse() {
    hasResponse = false;
    lengthSupplier = () -> 0;
    return this;
  }

  BackupApiResponseWriter withCheckpointState(final CheckpointStateResponse response) {
    responseWriter = response::write;
    lengthSupplier = response::getLength;
    return this;
  }

  BackupApiResponseWriter withBackupRanges(final BackupRangesResponse response) {
    responseWriter = response::write;
    lengthSupplier = response::getLength;
    return this;
  }

  @Override
  public void tryWriteResponse(
      final ServerOutput output, final int partitionId, final long requestId) {
    if (hasResponse) {
      try {
        response.reset().writer(this).setPartitionId(partitionId).setRequestId(requestId);
        output.sendResponse(response);
      } finally {
        reset();
      }
    }
  }

  @Override
  public void reset() {
    response.reset();
    hasResponse = true;
  }

  @Override
  public int getLength() {
    return lengthSupplier.getAsInt();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    responseWriter.accept(buffer, offset);
    return getLength();
  }
}
