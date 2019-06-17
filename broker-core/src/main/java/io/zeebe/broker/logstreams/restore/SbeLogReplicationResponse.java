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
import io.zeebe.distributedlog.restore.log.impl.DefaultLogReplicationResponse;
import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SbeLogReplicationResponse
    extends SbeBufferWriterReader<LogReplicationResponseEncoder, LogReplicationResponseDecoder>
    implements LogReplicationResponse {
  private final LogReplicationResponseEncoder encoder;
  private final LogReplicationResponseDecoder decoder;
  private final DefaultLogReplicationResponse delegate;

  public SbeLogReplicationResponse() {
    this.delegate = new DefaultLogReplicationResponse();
    this.encoder = new LogReplicationResponseEncoder();
    this.decoder = new LogReplicationResponseDecoder();
    reset();
  }

  public SbeLogReplicationResponse(LogReplicationResponse other) {
    this();
    delegate.setToPosition(other.getToPosition());
    delegate.setSerializedEvents(other.getSerializedEvents());
    delegate.setMoreAvailable(other.hasMoreAvailable());
  }

  public SbeLogReplicationResponse(byte[] serialized) {
    this();
    wrap(new UnsafeBuffer(serialized));
  }

  @Override
  public void reset() {
    super.reset();
    delegate.setToPosition(toPositionNullValue());
    delegate.setMoreAvailable(false);
    delegate.setSerializedEvents(new UnsafeBuffer(), 0, 0);
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);
    final DirectBuffer wrapBuffer = new UnsafeBuffer();
    decoder.wrapSerializedEvents(wrapBuffer);

    delegate.setToPosition(decoder.toPosition());
    delegate.setMoreAvailable(decoder.moreAvailable() == BooleanType.TRUE);
    delegate.setSerializedEvents(wrapBuffer, 0, wrapBuffer.capacity());
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);
    final byte[] serializedEvents = delegate.getSerializedEvents();

    encoder.toPosition(delegate.getToPosition());
    encoder.moreAvailable(delegate.hasMoreAvailable() ? BooleanType.TRUE : BooleanType.FALSE);

    if (getSerializedEventsLength() > 0) {
      encoder.putSerializedEvents(serializedEvents, 0, serializedEvents.length);
    } else {
      encoder.putSerializedEvents(new UnsafeBuffer(), 0, 0);
    }
  }

  @Override
  public int getLength() {
    return super.getLength() + serializedEventsHeaderLength() + getSerializedEventsLength();
  }

  @Override
  public boolean isValid() {
    return delegate.isValid()
        && getSerializedEventsLength() > 0
        && delegate.getToPosition() != toPositionNullValue();
  }

  @Override
  public long getToPosition() {
    return delegate.getToPosition();
  }

  @Override
  public boolean hasMoreAvailable() {
    return delegate.hasMoreAvailable();
  }

  @Override
  public byte[] getSerializedEvents() {
    return delegate.getSerializedEvents();
  }

  @Override
  public String toString() {
    return "SbeLogReplicationResponse{" + "delegate=" + delegate + "} " + super.toString();
  }

  public static byte[] serialize(LogReplicationResponse response) {
    return new SbeLogReplicationResponse(response).toBytes();
  }

  private int getSerializedEventsLength() {
    final byte[] serializedEvents = delegate.getSerializedEvents();
    return serializedEvents != null ? serializedEvents.length : 0;
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
