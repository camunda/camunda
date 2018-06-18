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
package io.zeebe.broker.job;

import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.collection.CompactList;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;

public class CreditsRequest implements BufferReader {
  protected static final int LENGTH = BitUtil.SIZE_OF_LONG + BitUtil.SIZE_OF_INT;
  protected static final int TYPE = 42;

  protected UnsafeBuffer content = new UnsafeBuffer(new byte[LENGTH]);

  public CreditsRequest() {}

  @Override
  public void wrap(DirectBuffer buffer, int index, int length) {
    if (length != LENGTH) {
      throw new RuntimeException("Unexpected message length");
    }

    this.content.putBytes(0, buffer, index, length);
  }

  public long getSubscriberKey() {
    return content.getLong(0);
  }

  public void setSubscriberKey(long subscriberKey) {
    this.content.putLong(0, subscriberKey);
  }

  public int getCredits() {
    return content.getInt(BitUtil.SIZE_OF_LONG);
  }

  public void setCredits(int credits) {
    this.content.putInt(BitUtil.SIZE_OF_LONG, credits);
  }

  /**
   * @param ringBuffer
   * @return true if success
   */
  public boolean writeTo(OneToOneRingBuffer ringBuffer) {
    return ringBuffer.write(TYPE, content, 0, LENGTH);
  }

  public void appendTo(CompactList list) {
    list.add(content);
  }

  public void wrapListElement(CompactList list, int index) {
    list.wrap(index, content);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof CreditsRequest) || obj == null) {
      return false;
    }

    final CreditsRequest request = (CreditsRequest) obj;

    return request.content.equals(this.content);
  }

  @Override
  public int hashCode() {
    return content.hashCode();
  }
}
