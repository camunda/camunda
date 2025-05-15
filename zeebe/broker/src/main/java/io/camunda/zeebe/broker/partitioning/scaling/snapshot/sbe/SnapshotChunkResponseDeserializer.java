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
import java.util.UUID;
import org.agrona.DirectBuffer;

public class SnapshotChunkResponseDeserializer {

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final SnapshotChunkResponseDecoder decoder = new SnapshotChunkResponseDecoder();

  public SnapshotChunkResponse deserialize(
      final DirectBuffer buffer, final int offset, final int capacity) {
    decoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
    final var transferId = new UUID(decoder.transferId().high(), decoder.transferId().low());
    final var totalCount = decoder.totalCount();
    final var checksum = decoder.checksum();
    final var fileBlockPosition = decoder.fileBlockPosition();
    final var totalFileSize = decoder.totalFileSize();
    final var snapshotId = decoder.snapshotId();
    final var chunkName = decoder.chunkName();
    final var content = new byte[decoder.contentLength()];
    decoder.getContent(content, 0, decoder.contentLength());
    final var chunk =
        new SnapshotChunkRecord(
            snapshotId, totalCount, chunkName, checksum, content, fileBlockPosition, totalFileSize);
    return new SnapshotChunkResponse(transferId, chunk);
  }
}
