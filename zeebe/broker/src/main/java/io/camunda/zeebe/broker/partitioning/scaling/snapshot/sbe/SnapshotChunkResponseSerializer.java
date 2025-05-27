/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotChunkRecord;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotChunkResponse;
import org.agrona.MutableDirectBuffer;

public class SnapshotChunkResponseSerializer {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final SnapshotChunkResponseEncoder encoder = new SnapshotChunkResponseEncoder();

  public int serialize(
      final SnapshotChunkResponse response, final MutableDirectBuffer buffer, final int offset) {
    final var transferId = response.transferId();
    final var chunk = response.chunk().orElse(SnapshotChunkRecord.empty());
    encoder.wrapAndApplyHeader(buffer, offset, headerEncoder);
    encoder
        .transferId()
        .high(transferId.getMostSignificantBits())
        .low(transferId.getLeastSignificantBits());
    encoder
        .totalCount(chunk.getTotalCount())
        .checksum(chunk.getChecksum())
        .fileBlockPosition(chunk.getFileBlockPosition())
        .totalFileSize(chunk.getTotalFileSize())
        .snapshotId(chunk.getSnapshotId())
        .chunkName(chunk.getChunkName());
    encoder.putContent(chunk.getContent(), 0, chunk.getContent().length);
    return encoder.encodedLength() + headerEncoder.encodedLength();
  }

  public int size(final SnapshotChunkResponse snapshotChunk) {
    int length =
        MessageHeaderEncoder.ENCODED_LENGTH
            + SnapshotChunkResponseEncoder.BLOCK_LENGTH
            + Integer.BYTES * 3;
    if (snapshotChunk.chunk().isPresent()) {
      final var chunk = snapshotChunk.chunk().get();
      length +=
          chunk.getChunkName().length()
              + chunk.getSnapshotId().length()
              + chunk.getContent().length;
    }
    return length;
  }
}
