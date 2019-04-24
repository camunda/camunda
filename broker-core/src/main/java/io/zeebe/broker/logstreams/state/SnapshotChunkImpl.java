/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.state;

import static io.zeebe.clustering.management.SnapshotChunkDecoder.snapshotPositionNullValue;
import static io.zeebe.clustering.management.SnapshotChunkDecoder.totalCountNullValue;
import static io.zeebe.clustering.management.SnapshotChunkEncoder.chunkNameHeaderLength;
import static io.zeebe.clustering.management.SnapshotChunkEncoder.contentHeaderLength;

import io.zeebe.broker.util.SbeBufferWriterReader;
import io.zeebe.clustering.management.SnapshotChunkDecoder;
import io.zeebe.clustering.management.SnapshotChunkEncoder;
import io.zeebe.logstreams.processor.SnapshotChunk;
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

  private final DirectBuffer content = new UnsafeBuffer(0, 0);

  public SnapshotChunkImpl() {}

  public SnapshotChunkImpl(SnapshotChunk chunk) {
    snapshotPosition = chunk.getSnapshotPosition();
    totalCount = chunk.getTotalCount();
    chunkName = chunk.getChunkName();
    content.wrap(chunk.getContent());
  }

  @Override
  public int getLength() {
    return super.getLength()
        + chunkNameHeaderLength()
        + chunkName.length()
        + contentHeaderLength()
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
        .putContent(content, 0, content.capacity());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);

    snapshotPosition = decoder.snapshotPosition();
    totalCount = decoder.totalCount();
    chunkName = decoder.chunkName();
    decoder.wrapContent(content);
  }

  @Override
  public void reset() {
    super.reset();

    snapshotPosition = snapshotPositionNullValue();
    totalCount = totalCountNullValue();

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
  public byte[] getContent() {
    return BufferUtil.bufferAsArray(content);
  }
}
