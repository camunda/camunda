/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe.GetSnapshotChunkSerializer;
import io.camunda.zeebe.transport.ClientRequest;
import io.camunda.zeebe.transport.RequestType;
import java.util.Optional;
import java.util.UUID;
import org.agrona.MutableDirectBuffer;

public record GetSnapshotChunk(
    int partitionId, UUID transferId, Optional<String> snapshotId, Optional<String> lastChunkName) {

  ClientRequest clientRequest() {
    return new ClientRequest() {
      int length = 0;

      @Override
      public int getPartitionId() {
        return partitionId;
      }

      @Override
      public RequestType getRequestType() {
        return RequestType.SNAPSHOT;
      }

      @Override
      public int getLength() {
        return 0;
      }

      @Override
      public void write(final MutableDirectBuffer buffer, final int offset) {
        final var serializer = new GetSnapshotChunkSerializer();
        length = serializer.serialize(GetSnapshotChunk.this, buffer, offset);
      }
    };
  }
}
