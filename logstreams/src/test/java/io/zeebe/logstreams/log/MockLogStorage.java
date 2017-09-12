/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.log;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.*;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.*;
import static io.zeebe.logstreams.log.LogStreamUtil.INVALID_ADDRESS;
import static io.zeebe.logstreams.log.LogStreamUtil.MAX_READ_EVENT_SIZE;
import static io.zeebe.util.StringUtil.getBytes;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import io.zeebe.logstreams.spi.LogStorage;
import org.agrona.concurrent.UnsafeBuffer;
import org.mockito.invocation.InvocationOnMock;

public class MockLogStorage
{
    private final LogStorage mockLogStorage;

    public MockLogStorage()
    {
        this.mockLogStorage = mock(LogStorage.class);

        // default behavior
        when(mockLogStorage.read(any(ByteBuffer.class), anyLong())).thenReturn(LogStorage.OP_RESULT_NO_DATA);
    }

    public MockLogStorage add(MockLogEntryBuilder builder)
    {
        builder.build(mockLogStorage);

        return this;
    }

    public MockLogStorage firstBlockAddress(long address)
    {
        when(mockLogStorage.getFirstBlockAddress()).thenReturn(address);

        return this;
    }

    public LogStorage getMock()
    {
        return mockLogStorage;
    }

    public static MockLogEntryBuilder newLogEntry()
    {
        return new MockLogEntryBuilder(1);
    }

    public static MockLogEntryBuilder newLogEntries(int amount)
    {
        return new MockLogEntryBuilder(amount);
    }

    public static class MockLogEntryBuilder
    {
        private long address = 0;
        private long nextAddress = 0;

        private long position = 0;
        private long raftTerm = -1;

        private byte[] sourceEventLogStreamTopicName = null;
        private int sourceEventLogStreamPartitionId = 1;
        private long sourceEventPosition = -1L;

        private int producerId = -1;

        private long key = 0;
        private int messageLength = 0;

        private byte[] value = null;

        private byte[] metadata = null;
        private short metadataLength = 0;
        private short topicNameLength = 0;

        private final int amount;
        private boolean partlyRead;

        private long readCount = 0L;
        private long maxPosition = -1;

        public MockLogEntryBuilder(int amount)
        {
            this.amount = amount;
        }

        public MockLogEntryBuilder address(long address)
        {
            this.address = address;
            return this;
        }

        public MockLogEntryBuilder position(long position)
        {
            this.position = position;
            return this;
        }

        public MockLogEntryBuilder maxPosition(long position)
        {
            this.maxPosition = position;
            return this;
        }

        public MockLogEntryBuilder sourceEventLogStreamTopicName(String topicName)
        {
            this.sourceEventLogStreamTopicName = getBytes(topicName);
            return this;
        }

        public MockLogEntryBuilder sourceEventLogStreamId(int logStreamId)
        {
            this.sourceEventLogStreamPartitionId = logStreamId;
            return this;
        }

        public MockLogEntryBuilder sourceEventPosition(long position)
        {
            this.sourceEventPosition = position;
            return this;
        }

        public MockLogEntryBuilder producerId(int producerId)
        {
            this.producerId = producerId;
            return this;
        }

        public MockLogEntryBuilder key(long key)
        {
            this.key = key;
            return this;
        }

        public MockLogEntryBuilder raftTerm(int raftTerm)
        {
            this.raftTerm = raftTerm;
            return this;
        }

        public MockLogEntryBuilder value(byte[] value)
        {
            this.value = value;
            return this;
        }

        public MockLogEntryBuilder metadata(byte[] metadata)
        {
            this.metadata = metadata;
            return this;
        }

        public MockLogEntryBuilder messageLength(int messageLength)
        {
            this.messageLength = messageLength;
            return this;
        }

        public MockLogEntryBuilder nextAddress(long nextAddress)
        {
            this.nextAddress = nextAddress;
            return this;
        }

        public MockLogEntryBuilder partlyRead()
        {
            this.partlyRead = true;
            return this;
        }

        public void build(LogStorage mockLogStorage)
        {
            preBuild();

            if (partlyRead)
            {
                when(mockLogStorage.read(any(ByteBuffer.class), anyLong())).thenAnswer(this::readBufferPartly);
            }
            else
            {
                when(mockLogStorage.read(any(ByteBuffer.class), eq(address))).thenAnswer(this::readBuffer);
            }
        }

        private void preBuild()
        {
            topicNameLength = (short) (sourceEventLogStreamTopicName != null ? sourceEventLogStreamTopicName.length : 0);
            metadataLength = (short) (metadata != null ? metadata.length : 0);
            final int headerLength = headerLength(topicNameLength, metadataLength);

            // min. message length
            messageLength = Math.max(messageLength, headerLength);

            if (value == null)
            {
                value = new byte[messageLength - headerLength];
                new Random().nextBytes(value);
            }
            else
            {
                messageLength = headerLength + value.length;
            }
            readCount = position;
        }

        public long readBufferPartly(InvocationOnMock invocation)
        {
            final ByteBuffer byteBuffer = (ByteBuffer) invocation.getArguments()[0];
            final long address = (Long) invocation.getArguments()[1];
            position = readCount;

            final long result;
            if (maxPosition == INVALID_ADDRESS ||
                position <= maxPosition)
            {
                final long position = byteBuffer.position();
                final long limit = byteBuffer.limit();

                if (limit == LogStreamUtil.HEADER_LENGTH)
                {
                    readHeader(invocation);
                }
                else
                {
                    if (limit != MAX_READ_EVENT_SIZE)
                    {
                        readCount++;
                    }
                    readRest(invocation);
                }

                final long readBytes = limit - position;
                result = address + readBytes;
            }
            else
            {
                result = LogStorage.OP_RESULT_NO_DATA;
            }

            return result;
        }

        public void readHeader(InvocationOnMock invocation)
        {
            final ByteBuffer byteBuffer = (ByteBuffer) invocation.getArguments()[0];
            final UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);

            int offset = byteBuffer.position();
            buffer.putInt(lengthOffset(offset), messageLength);

            offset = messageOffset(offset);
            setPosition(buffer, offset, position);
            setRaftTerm(buffer, offset, raftTerm);
            setProducerId(buffer, offset, producerId);
            setSourceEventLogStreamPartitionId(buffer, offset, sourceEventLogStreamPartitionId);
            setSourceEventPosition(buffer, offset, sourceEventPosition);
            setKey(buffer, offset, key);
            setSourceEventLogStreamTopicNameLength(buffer, offset, topicNameLength);
            setMetadataLength(buffer, offset, metadataLength);

            byteBuffer.position(byteBuffer.limit());
        }

        public void readRest(InvocationOnMock invocation)
        {
            final ByteBuffer byteBuffer = (ByteBuffer) invocation.getArguments()[0];
            final UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);

            // the message is written to the beginning of the buffer, so we cannot use
            // messageOffset(offset) for the fields, as the data frame header is overwritten
            int offset = byteBuffer.position();

            if (offset < byteBuffer.limit())
            {
                if (sourceEventLogStreamTopicNameOffset(offset) < byteBuffer.limit())
                {
                    if (sourceEventLogStreamTopicName != null)
                    {
                        buffer.putBytes(sourceEventLogStreamTopicNameOffset(offset), sourceEventLogStreamTopicName);
                    }

                    if (metadata != null)
                    {
                        buffer.putBytes(metadataOffset(offset, topicNameLength), metadata);
                    }

                    if (value != null)
                    {
                        final int valueOffset = valueOffset(offset, topicNameLength, metadataLength);
                        final byte[] valueToWrite = Arrays.copyOf(value, Math.min(value.length, byteBuffer.limit() - valueOffset));
                        buffer.putBytes(valueOffset, valueToWrite);
                    }
                }
            }
            offset += alignedLength(messageLength);

            byteBuffer.position(Math.min(offset, byteBuffer.limit()));
        }

        public long readBuffer(InvocationOnMock invocation)
        {
            final ByteBuffer byteBuffer = (ByteBuffer) invocation.getArguments()[0];
            final UnsafeBuffer buffer = new UnsafeBuffer(byteBuffer);

            int offset = byteBuffer.position();

            for (int i = 0; i < amount; i++)
            {
                buffer.putInt(lengthOffset(offset), messageLength);

                final int messageOffset = messageOffset(offset);
                if (messageOffset < byteBuffer.limit())
                {
                    setPosition(buffer, messageOffset, position + i);
                    setRaftTerm(buffer, messageOffset, raftTerm);
                    setProducerId(buffer, messageOffset, producerId);
                    setSourceEventLogStreamPartitionId(buffer, messageOffset, sourceEventLogStreamPartitionId);
                    setSourceEventPosition(buffer, messageOffset, sourceEventPosition);

                    if (keyOffset(messageOffset) < byteBuffer.limit())
                    {
                        setKey(buffer, messageOffset, key + i);
                        setSourceEventLogStreamTopicNameLength(buffer, messageOffset, topicNameLength);
                        setMetadataLength(buffer, messageOffset, metadataLength);

                        if (sourceEventLogStreamTopicName != null)
                        {
                            buffer.putBytes(sourceEventLogStreamTopicNameOffset(messageOffset), sourceEventLogStreamTopicName);
                        }

                        if (metadata != null)
                        {
                            buffer.putBytes(metadataOffset(messageOffset, topicNameLength), metadata);
                        }

                        if (value != null)
                        {
                            final int valueOffset = valueOffset(messageOffset, topicNameLength, metadataLength);
                            final byte[] valueToWrite = Arrays.copyOf(value, Math.min(value.length, byteBuffer.limit() - valueOffset));
                            buffer.putBytes(valueOffset, valueToWrite);
                        }
                    }
                }
                offset += alignedLength(messageLength);
            }

            byteBuffer.position(Math.min(offset, byteBuffer.limit()));
            return nextAddress;
        }
    }
}
