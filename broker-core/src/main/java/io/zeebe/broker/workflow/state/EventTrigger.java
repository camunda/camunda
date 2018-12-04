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
package io.zeebe.broker.workflow.state;

import static io.zeebe.util.buffer.BufferUtil.readIntoBuffer;
import static io.zeebe.util.buffer.BufferUtil.writeIntoBuffer;

import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class EventTrigger implements BufferReader, BufferWriter {
  private final DirectBuffer handlerNodeId = new UnsafeBuffer(0, 0);
  private final DirectBuffer payload = new UnsafeBuffer(0, 0);

  public EventTrigger reset() {
    handlerNodeId.wrap(0, 0);
    payload.wrap(0, 0);

    return this;
  }

  public boolean isValid() {
    return handlerNodeId.capacity() > 0;
  }

  public DirectBuffer getHandlerNodeId() {
    return handlerNodeId;
  }

  public EventTrigger setHandlerNodeId(DirectBuffer handlerNodeId) {
    this.handlerNodeId.wrap(handlerNodeId);
    return this;
  }

  public DirectBuffer getPayload() {
    return payload;
  }

  public EventTrigger setPayload(DirectBuffer payload) {
    this.payload.wrap(payload);
    return this;
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    final int startOffset = offset;

    offset = readIntoBuffer(buffer, offset, handlerNodeId);
    offset = readIntoBuffer(buffer, offset, payload);

    assert (offset - startOffset) == length : "End offset differs from length";
  }

  @Override
  public int getLength() {
    return 2 * Integer.BYTES + handlerNodeId.capacity() + payload.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    final int startOffset = offset;

    offset = writeIntoBuffer(buffer, offset, handlerNodeId);
    offset = writeIntoBuffer(buffer, offset, payload);

    assert (offset - startOffset) == getLength() : "End offset differs from getLength()";
  }
}
