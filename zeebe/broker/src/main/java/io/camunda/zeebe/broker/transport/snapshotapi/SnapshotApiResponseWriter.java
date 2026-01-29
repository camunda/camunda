/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.snapshotapi;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe.SnapshotResponseSerializer;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.ResponseWriter;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.transport.impl.ServerResponseImpl;
import org.agrona.MutableDirectBuffer;

public class SnapshotApiResponseWriter implements ResponseWriter {
  private final ServerResponseImpl response = new ServerResponseImpl();
  private SnapshotResponse snapshotChunk;
  private final SnapshotResponseSerializer serializer = new SnapshotResponseSerializer();

  void setResponse(final SnapshotResponse response) {
    snapshotChunk = response;
  }

  @Override
  public void tryWriteResponse(
      final ServerOutput output, final int partitionId, final long requestId) {
    if (snapshotChunk != null) {
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
    //
  }

  // Note that length must be known before serializing it
  @Override
  public int getLength() {
    return serializer.size(snapshotChunk);
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    serializer.serialize(snapshotChunk, buffer, offset);
    return getLength();
  }
}
