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

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.broadcast.RecordDescriptor;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import java.util.function.Consumer;

public class CreditsRequestBuffer
{
    protected final int capacityUpperBound;
    protected final OneToOneRingBuffer ringBuffer;
    protected final RequestHandler requestHandler;

    public CreditsRequestBuffer(int capacityLowerBound, Consumer<CreditsRequest> requestConsumer)
    {
        final int bufferCapacity = requiredBufferCapacityForNumRequests(capacityLowerBound, CreditsRequest.LENGTH);

        // note: this is only an upper bound, because OneToOneRingBuffer alings the messages to a certain length
        // which we do not include in this calculation to avoid relying on agrona-internal concepts
        this.capacityUpperBound = numRequestsFittingInto(bufferCapacity, CreditsRequest.LENGTH);

        final UnsafeBuffer rawBuffer = new UnsafeBuffer(new byte[bufferCapacity]);
        this.ringBuffer = new OneToOneRingBuffer(rawBuffer);
        this.requestHandler = new RequestHandler(requestConsumer);
    }

    protected static int requiredBufferCapacityForNumRequests(int numRequests, int requestLength)
    {
        final int recordLength = RecordDescriptor.HEADER_LENGTH + requestLength;
        final int allRecordsLength = numRequests * recordLength;
        return BitUtil.findNextPositivePowerOfTwo(allRecordsLength) + RingBufferDescriptor.TRAILER_LENGTH;
    }

    protected static int numRequestsFittingInto(int bufferSize, int requestLength)
    {
        final int recordLength = RecordDescriptor.HEADER_LENGTH + requestLength;
        return (bufferSize - RingBufferDescriptor.TRAILER_LENGTH) / recordLength;
    }

    public int handleRequests()
    {
        int read = 0;
        while (ringBuffer.size() > 0)
        {
            read += ringBuffer.read(requestHandler);
        }
        return read;
    }

    public boolean offerRequest(CreditsRequest request)
    {
        return request.writeTo(ringBuffer);
    }

    public int getCapacityUpperBound()
    {
        return capacityUpperBound;
    }

    protected static class RequestHandler implements MessageHandler
    {
        protected final CreditsRequest request = new CreditsRequest();
        protected final Consumer<CreditsRequest> requestConsumer;

        public RequestHandler(Consumer<CreditsRequest> requestConsumer)
        {
            this.requestConsumer = requestConsumer;
        }

        @Override
        public void onMessage(int msgTypeId, MutableDirectBuffer buffer, int index, int length)
        {
            request.wrap(buffer, index, length);
            requestConsumer.accept(request);
        }

    }

}
