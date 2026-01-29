/*
 * Copyright © 2020  camunda services GmbH (info@camunda.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package io.atomix.raft.snapshot.impl;

import io.atomix.raft.snapshot.SbeBufferWriterReader;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.util.buffer.BufferUtil;
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
  private long fileBlockPosition;
  private long totalFileSize;

  public SnapshotChunkImpl() {}

  public SnapshotChunkImpl(final SnapshotChunk chunk) {
    snapshotId = chunk.getSnapshotId();
    totalCount = chunk.getTotalCount();
    chunkName = chunk.getChunkName();
    checksum = chunk.getChecksum();
    content.wrap(chunk.getContent());
    fileBlockPosition = chunk.getFileBlockPosition();
    totalFileSize = chunk.getTotalFileSize();
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
    fileBlockPosition = SnapshotChunkDecoder.fileBlockPositionNullValue();
    totalFileSize = SnapshotChunkDecoder.totalFileSizeNullValue();

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
  public int write(final MutableDirectBuffer buffer, final int offset) {
    final var len = super.write(buffer, offset);

    // The snapshot checksum is 0 for backwards compatibility reasons, when sending chunk data to
    // brokers on older versions which checks on the snapshot checksum.
    encoder
        .totalCount(totalCount)
        .fileBlockPosition(fileBlockPosition)
        .totalFileSize(totalFileSize)
        .snapshotId(snapshotId)
        .chunkName(chunkName)
        .checksum(checksum)
        .snapshotChecksum(0)
        .putContent(content, 0, content.capacity());
    return len + encoder.encodedLength();
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    super.wrap(buffer, offset, length);

    totalCount = decoder.totalCount();
    fileBlockPosition = decoder.fileBlockPosition();
    totalFileSize = decoder.totalFileSize();
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
  public long getFileBlockPosition() {
    // backwards compatability
    if (fileBlockPosition == SnapshotChunkDecoder.fileBlockPositionNullValue()) {
      return 0;
    }

    return fileBlockPosition;
  }

  @Override
  public long getTotalFileSize() {
    // backwards comptability
    if (totalFileSize == SnapshotChunkDecoder.totalFileSizeNullValue()) {
      return getContent().length;
    }

    return totalFileSize;
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
        + ", fileBlockPosition="
        + fileBlockPosition
        + ", totalFileSize="
        + totalFileSize
        + "} "
        + super.toString();
  }
}
