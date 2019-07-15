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

public class SnapshotChunkImpl
    extends SbeBufferWriterReader<SnapshotChunkEncoder, SnapshotChunkDecoder>
    implements SnapshotChunk {

  private final SnapshotChunkEncoder encoder = new SnapshotChunkEncoder();
  private final SnapshotChunkDecoder decoder = new SnapshotChunkDecoder();

  private long snapshotPosition;
  private int totalCount;
  private String chunkName;
  private long checksum;

  private final DirectBuffer content = new UnsafeBuffer(0, 0);

  public SnapshotChunkImpl() {}

  public SnapshotChunkImpl(SnapshotChunk chunk) {
    snapshotPosition = chunk.getSnapshotPosition();
    totalCount = chunk.getTotalCount();
    chunkName = chunk.getChunkName();
    checksum = chunk.getChecksum();
    content.wrap(chunk.getContent());
  }

  @Override
  public int getLength() {
    return super.getLength()
        + SnapshotChunkEncoder.chunkNameHeaderLength()
        + chunkName.length()
        + SnapshotChunkEncoder.contentHeaderLength()
        + content.capacity();
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
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);

    encoder
        .snapshotPosition(snapshotPosition)
        .totalCount(totalCount)
        .chunkName(chunkName)
        .checksum(checksum)
        .putContent(content, 0, content.capacity());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);

    snapshotPosition = decoder.snapshotPosition();
    totalCount = decoder.totalCount();
    chunkName = decoder.chunkName();
    checksum = decoder.checksum();
    decoder.wrapContent(content);
  }

  @Override
  public void reset() {
    super.reset();

    snapshotPosition = SnapshotChunkDecoder.snapshotPositionNullValue();
    totalCount = SnapshotChunkDecoder.totalCountNullValue();
    checksum = SnapshotChunkDecoder.checksumNullValue();

    chunkName = "";
    content.wrap(0, 0);
  }

  @Override
  public long getSnapshotPosition() {
    return snapshotPosition;
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
        + "snapshotPosition="
        + snapshotPosition
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
