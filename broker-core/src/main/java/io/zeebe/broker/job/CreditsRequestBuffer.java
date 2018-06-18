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

import io.zeebe.util.sched.channel.OneToOneRingBufferChannel;
import org.agrona.BitUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.broadcast.RecordDescriptor;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

public class CreditsRequestBuffer extends OneToOneRingBufferChannel {
  protected final int capacityUpperBound;

  public CreditsRequestBuffer(final int numRequests) {
    super(new UnsafeBuffer(new byte[requiredBufferCapacityForNumRequests(numRequests)]));

    // note: this is only an upper bound, because OneToOneRingBuffer alings the messages to a
    // certain length
    // which we do not include in this calculation to avoid relying on agrona-internal concepts
    this.capacityUpperBound = numRequestsFittingInto(numRequests);
  }

  public int getCapacityUpperBound() {
    return capacityUpperBound;
  }

  protected static int requiredBufferCapacityForNumRequests(final int numRequests) {
    final int recordLength = RecordDescriptor.HEADER_LENGTH + CreditsRequest.LENGTH;
    final int allRecordsLength = numRequests * recordLength;
    return BitUtil.findNextPositivePowerOfTwo(allRecordsLength)
        + RingBufferDescriptor.TRAILER_LENGTH;
  }

  protected static int numRequestsFittingInto(final int numRequests) {
    final int recordLength = RecordDescriptor.HEADER_LENGTH + CreditsRequest.LENGTH;
    final int allRecordsLength = numRequests * recordLength;
    return BitUtil.findNextPositivePowerOfTwo(allRecordsLength) / recordLength;
  }
}
