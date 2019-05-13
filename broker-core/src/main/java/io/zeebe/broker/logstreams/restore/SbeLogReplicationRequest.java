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

import static io.zeebe.clustering.management.LogReplicationRequestEncoder.fromPositionNullValue;
import static io.zeebe.clustering.management.LogReplicationRequestEncoder.toPositionNullValue;

import io.zeebe.clustering.management.LogReplicationRequestDecoder;
import io.zeebe.clustering.management.LogReplicationRequestEncoder;
import io.zeebe.distributedlog.restore.log.LogReplicationRequest;
import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SbeLogReplicationRequest
    extends SbeBufferWriterReader<LogReplicationRequestEncoder, LogReplicationRequestDecoder>
    implements LogReplicationRequest {
  private final LogReplicationRequestEncoder encoder = new LogReplicationRequestEncoder();
  private final LogReplicationRequestDecoder decoder = new LogReplicationRequestDecoder();

  private long fromPosition;
  private long toPosition;

  public SbeLogReplicationRequest() {}

  public SbeLogReplicationRequest(LogReplicationRequest other) {
    reset();
    wrap(other);
  }

  public SbeLogReplicationRequest(long fromPosition, long toPosition) {
    this.fromPosition = fromPosition;
    this.toPosition = toPosition;
  }

  public SbeLogReplicationRequest(byte[] serialized) {
    reset();
    wrap(new UnsafeBuffer(serialized));
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);
    fromPosition = decoder.fromPosition();
    toPosition = decoder.toPosition();
  }

  public void wrap(LogReplicationRequest other) {
    setFromPosition(other.getFromPosition());
    setToPosition(other.getToPosition());
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);
    encoder.fromPosition(fromPosition).toPosition(toPosition);
  }

  @Override
  public void reset() {
    super.reset();
    fromPosition = fromPositionNullValue();
    toPosition = toPositionNullValue();
  }

  @Override
  public long getFromPosition() {
    return fromPosition;
  }

  public void setFromPosition(long fromPosition) {
    this.fromPosition = fromPosition;
  }

  @Override
  public long getToPosition() {
    return toPosition;
  }

  public void setToPosition(long toPosition) {
    this.toPosition = toPosition;
  }

  public static byte[] serialize(LogReplicationRequest request) {
    return new SbeLogReplicationRequest(request).toBytes();
  }

  @Override
  protected LogReplicationRequestEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected LogReplicationRequestDecoder getBodyDecoder() {
    return decoder;
  }
}
