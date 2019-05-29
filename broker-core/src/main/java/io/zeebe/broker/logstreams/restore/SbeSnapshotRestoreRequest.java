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
package io.zeebe.broker.logstreams.restore;

import static io.zeebe.clustering.management.SnapshotRestoreRequestEncoder.chunkIdxNullValue;
import static io.zeebe.clustering.management.SnapshotRestoreRequestEncoder.snapshotIdNullValue;

import io.zeebe.clustering.management.SnapshotRestoreRequestDecoder;
import io.zeebe.clustering.management.SnapshotRestoreRequestEncoder;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreRequest;
import io.zeebe.distributedlog.restore.snapshot.impl.DefaultSnapshotRestoreRequest;
import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SbeSnapshotRestoreRequest
    extends SbeBufferWriterReader<SnapshotRestoreRequestEncoder, SnapshotRestoreRequestDecoder>
    implements SnapshotRestoreRequest {

  private final SnapshotRestoreRequestEncoder encoder;
  private final SnapshotRestoreRequestDecoder decoder;

  private final DefaultSnapshotRestoreRequest delegate;

  public SbeSnapshotRestoreRequest() {
    delegate = new DefaultSnapshotRestoreRequest();
    decoder = new SnapshotRestoreRequestDecoder();
    encoder = new SnapshotRestoreRequestEncoder();
    reset();
  }

  public SbeSnapshotRestoreRequest(SnapshotRestoreRequest request) {
    this();
    setChunkIdx(request.getChunkIdx());
    setSnapshotId(request.getSnapshotId());
  }

  public SbeSnapshotRestoreRequest(byte[] bytes) {
    this();
    wrap(new UnsafeBuffer(bytes));
  }

  @Override
  public void reset() {
    super.reset();
    setSnapshotId(snapshotIdNullValue());
    setChunkIdx(chunkIdxNullValue());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);
    setSnapshotId(decoder.snapshotId());
    setChunkIdx(decoder.chunkIdx());
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);
    encoder.snapshotId(getSnapshotId());
    encoder.chunkIdx(getChunkIdx());
  }

  private void setChunkIdx(int chunkIdx) {
    delegate.setChunkIdx(chunkIdx);
  }

  private void setSnapshotId(long snaphshotId) {
    delegate.setSnapshotId(snaphshotId);
  }

  @Override
  public long getSnapshotId() {
    return delegate.getSnapshotId();
  }

  @Override
  public int getChunkIdx() {
    return delegate.getChunkIdx();
  }

  @Override
  protected SnapshotRestoreRequestEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected SnapshotRestoreRequestDecoder getBodyDecoder() {
    return decoder;
  }

  public static byte[] serialize(SnapshotRestoreRequest request) {
    return new SbeSnapshotRestoreRequest(request).toBytes();
  }
}
