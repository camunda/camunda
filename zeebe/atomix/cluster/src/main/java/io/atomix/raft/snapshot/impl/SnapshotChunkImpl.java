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
  private String chunkId;
  private String snapshotId;
  private int totalCount;
  private String chunkName;
  private long checksum;
  private long snapshotChecksum;

  public SnapshotChunkImpl() {}

  public SnapshotChunkImpl(final SnapshotChunk chunk) {
    snapshotId = chunk.getSnapshotId();
    totalCount = chunk.getTotalCount();
    chunkName = chunk.getChunkName();
    checksum = chunk.getChecksum();
    snapshotChecksum = chunk.getSnapshotChecksum();
    content.wrap(chunk.getContent());
    chunkId = chunk.getChunkId();
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
    snapshotChecksum = SnapshotChunkDecoder.snapshotChecksumNullValue();

    snapshotId = "";
    chunkName = "";
    chunkId = "";
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
        + content.capacity()
        + SnapshotChunkEncoder.chunkIdHeaderLength()
        + chunkId.length();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    encoder
        .totalCount(totalCount)
        .snapshotId(snapshotId)
        .chunkName(chunkName)
        .checksum(checksum)
        .snapshotChecksum(snapshotChecksum)
        .chunkId(chunkId)
        .putContent(content, 0, content.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    super.wrap(buffer, offset, length);

    totalCount = decoder.totalCount();
    snapshotId = decoder.snapshotId();
    chunkName = decoder.chunkName();
    checksum = decoder.checksum();
    snapshotChecksum = decoder.snapshotChecksum();
    chunkId = decoder.chunkId();

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
  public long getSnapshotChecksum() {
    return snapshotChecksum;
  }

  @Override
  public String getChunkId() {
    return chunkId;
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
        + ", snapshotChecksum="
        + snapshotChecksum
        + ", chunkId='"
        + chunkId
        + "} "
        + super.toString();
  }
}
