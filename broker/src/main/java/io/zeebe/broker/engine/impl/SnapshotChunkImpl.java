/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine.impl;

import io.zeebe.clustering.management.SnapshotChunkDecoder;
import io.zeebe.clustering.management.SnapshotChunkEncoder;
import io.zeebe.engine.util.SbeBufferWriterReader;
import io.zeebe.logstreams.state.SnapshotChunk;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class SnapshotChunkImpl
    extends SbeBufferWriterReader<SnapshotChunkEncoder, SnapshotChunkDecoder>
    implements SnapshotChunk {

  private final SnapshotChunkEncoder encoder = new SnapshotChunkEncoder();
  private final SnapshotChunkDecoder decoder = new SnapshotChunkDecoder();
  private final DirectBuffer content = new UnsafeBuffer(0, 0);
  private String snapshotId;
  private int totalCount;
  private String chunkName;
  private long checksum;

  public SnapshotChunkImpl() {}

  public SnapshotChunkImpl(final SnapshotChunk chunk) {
    snapshotId = chunk.getSnapshotId();
    totalCount = chunk.getTotalCount();
    chunkName = chunk.getChunkName();
    checksum = chunk.getChecksum();
    content.wrap(chunk.getContent());
  }

  @Override
  protected SnapshotChunkEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected SnapshotChunkDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public void reset() {
    super.reset();

    totalCount = SnapshotChunkDecoder.totalCountNullValue();
    checksum = SnapshotChunkDecoder.checksumNullValue();

    snapshotId = "";
    chunkName = "";
    content.wrap(0, 0);
  }

  @Override
  public int getLength() {
    return super.getLength()
        + SnapshotChunkEncoder.snapshotIdHeaderLength()
        + snapshotId.length()
        + SnapshotChunkEncoder.chunkNameHeaderLength()
        + chunkName.length()
        + SnapshotChunkEncoder.contentHeaderLength()
        + content.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    encoder
        .totalCount(totalCount)
        .snapshotId(snapshotId)
        .chunkName(chunkName)
        .checksum(checksum)
        .putContent(content, 0, content.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    super.wrap(buffer, offset, length);

    totalCount = decoder.totalCount();
    snapshotId = decoder.snapshotId();
    chunkName = decoder.chunkName();
    checksum = decoder.checksum();

    if (decoder.contentLength() > 0) {
      decoder.wrapContent(content);
    }
  }

  @Override
  public String getSnapshotId() {
    return snapshotId;
  }

  @Override
  public int getTotalCount() {
    return totalCount;
  }

  @Override
  public String getChunkName() {
    return chunkName;
  }

  @Override
  public long getChecksum() {
    return checksum;
  }

  @Override
  public byte[] getContent() {
    return BufferUtil.bufferAsArray(content);
  }

  @Override
  public String toString() {
    return "SnapshotChunkImpl{"
        + "snapshotId="
        + snapshotId
        + ", totalCount="
        + totalCount
        + ", chunkName='"
        + chunkName
        + '\''
        + ", checksum="
        + checksum
        + "} "
        + super.toString();
  }
}
