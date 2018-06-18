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
package io.zeebe.broker.transport.controlmessage;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import io.zeebe.protocol.Protocol;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ControlMessageRequestHeaderDescriptor {
  public static final int STREAM_ID_OFFSET;
  public static final int REQUEST_ID_OFFSET;

  public static final int HEADER_LENGTH;

  static {
    int offset = 0;

    STREAM_ID_OFFSET = offset;
    offset += SIZE_OF_INT;

    REQUEST_ID_OFFSET = offset;
    offset += SIZE_OF_LONG;

    HEADER_LENGTH = offset;
  }

  public static int framedLength(int messageLength) {
    return HEADER_LENGTH + messageLength;
  }

  public static int headerLength() {
    return HEADER_LENGTH;
  }

  protected final UnsafeBuffer buffer = new UnsafeBuffer(new byte[HEADER_LENGTH]);

  public ControlMessageRequestHeaderDescriptor wrap(DirectBuffer buffer, int offset) {
    this.buffer.wrap(buffer, offset, HEADER_LENGTH);
    return this;
  }

  public ControlMessageRequestHeaderDescriptor streamId(int streamId) {
    buffer.putInt(STREAM_ID_OFFSET, streamId, Protocol.ENDIANNESS);
    return this;
  }

  public ControlMessageRequestHeaderDescriptor requestId(long requestId) {
    buffer.putLong(REQUEST_ID_OFFSET, requestId, Protocol.ENDIANNESS);
    return this;
  }

  public int streamId() {
    return buffer.getInt(STREAM_ID_OFFSET, Protocol.ENDIANNESS);
  }

  public long requestId() {
    return buffer.getLong(REQUEST_ID_OFFSET, Protocol.ENDIANNESS);
  }
}
