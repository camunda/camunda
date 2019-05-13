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

import static io.zeebe.clustering.management.LogReplicationResponseEncoder.serializedEventsHeaderLength;
import static io.zeebe.clustering.management.LogReplicationResponseEncoder.toPositionNullValue;

import io.zeebe.clustering.management.BooleanType;
import io.zeebe.clustering.management.LogReplicationResponseDecoder;
import io.zeebe.clustering.management.LogReplicationResponseEncoder;
import io.zeebe.distributedlog.restore.log.LogReplicationResponse;
import io.zeebe.engine.util.SbeBufferWriterReader;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SbeLogReplicationResponse
    extends SbeBufferWriterReader<LogReplicationResponseEncoder, LogReplicationResponseDecoder>
    implements LogReplicationResponse {
  private final LogReplicationResponseEncoder encoder = new LogReplicationResponseEncoder();
  private final LogReplicationResponseDecoder decoder = new LogReplicationResponseDecoder();
  private final DirectBuffer serializedEvents = new UnsafeBuffer();

  private long toPosition;
  private boolean moreAvailable;

  public SbeLogReplicationResponse() {}

  public SbeLogReplicationResponse(LogReplicationResponse other) {
    reset();
    wrap(other);
  }

  public SbeLogReplicationResponse(
      long toPosition, boolean moreAvailable, byte[] serializedEvents) {
    this.toPosition = toPosition;
    this.moreAvailable = moreAvailable;
    this.serializedEvents.wrap(serializedEvents);
  }

  public SbeLogReplicationResponse(byte[] serialized) {
    reset();
    wrap(new UnsafeBuffer(serialized));
  }

  @Override
  public void reset() {
    super.reset();
    toPosition = toPositionNullValue();
    moreAvailable = false;
    serializedEvents.wrap(0, 0);
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);

    toPosition = decoder.toPosition();
    moreAvailable = decoder.moreAvailable() == BooleanType.TRUE;
    decoder.wrapSerializedEvents(serializedEvents);
  }

  public void wrap(LogReplicationResponse other) {
    this.setToPosition(other.getToPosition());
    this.setSerializedEvents(other.getSerializedEvents());
    this.setMoreAvailable(other.hasMoreAvailable());
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);
    encoder.toPosition(toPosition);
    encoder.putSerializedEvents(serializedEvents, 0, serializedEvents.capacity());
    encoder.moreAvailable(moreAvailable ? BooleanType.TRUE : BooleanType.FALSE);
  }

  @Override
  public int getLength() {
    return super.getLength() + serializedEventsHeaderLength() + serializedEvents.capacity();
  }

  @Override
  public boolean hasMoreAvailable() {
    return moreAvailable;
  }

  @Override
  public byte[] getSerializedEvents() {
    return BufferUtil.bufferAsArray(serializedEvents);
  }

  @Override
  public long getToPosition() {
    return toPosition;
  }

  public void setToPosition(long toPosition) {
    this.toPosition = toPosition;
  }

  public void setMoreAvailable(boolean moreAvailable) {
    this.moreAvailable = moreAvailable;
  }

  public void setSerializedEvents(byte[] serializedEvents) {
    this.serializedEvents.wrap(serializedEvents);
  }

  public void setSerializedEvents(DirectBuffer serializedEvents, int offset, int length) {
    this.serializedEvents.wrap(serializedEvents, offset, length);
  }

  public static byte[] serialize(LogReplicationResponse response) {
    return new SbeLogReplicationResponse(response).toBytes();
  }

  @Override
  public boolean isValid() {
    return toPosition != toPositionNullValue() && serializedEvents.capacity() > 0;
  }

  @Override
  protected LogReplicationResponseEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected LogReplicationResponseDecoder getBodyDecoder() {
    return decoder;
  }
}
