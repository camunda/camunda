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

import static io.zeebe.dispatcher.impl.PositionUtil.position;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.FRAME_ALIGNMENT;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedFramedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.headerLength;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.keyOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.metadataOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.positionOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.producerIdOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.raftTermOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.sourceEventLogStreamPartitionIdOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.sourceEventPositionOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.valueOffset;
import static io.zeebe.util.StringUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import io.zeebe.dispatcher.ClaimedFragmentBatch;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.impl.log.LogBufferAppender;
import io.zeebe.util.buffer.DirectBufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

public class LogStreamBatchWriterTest
{
    private static final int PARTITION_ID = 1;
    private static final int PARTITION_OFFSET = 0;

    private static final byte[] EVENT_VALUE_1 = getBytes("value1");
    private static final byte[] EVENT_VALUE_2 = getBytes("value2");

    private static final UnsafeBuffer EVENT_VALUE_BUFFER_1 = new UnsafeBuffer(EVENT_VALUE_1);
    private static final UnsafeBuffer EVENT_VALUE_BUFFER_2 = new UnsafeBuffer(EVENT_VALUE_2);

    private static final byte[] EVENT_METADATA_1 = getBytes("metadata1");
    private static final byte[] EVENT_METADATA_2 = getBytes("metadata2");

    private static final byte[] TOPIC_NAME = "topic".getBytes();
    private static final DirectBuffer TOPIC_NAME_BUFFER = new UnsafeBuffer(TOPIC_NAME);

    @Mock
    private LogStream mockLog;

    @Mock
    private Dispatcher mockWriteBuffer;

    private UnsafeBuffer writeBuffer;

    private LogStreamBatchWriter writer;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init() throws Exception
    {
        MockitoAnnotations.initMocks(this);

        when(mockLog.getWriteBuffer()).thenReturn(mockWriteBuffer);
        when(mockLog.getTopicName()).thenReturn(TOPIC_NAME_BUFFER);
        when(mockLog.getPartitionId()).thenReturn(PARTITION_ID);

        writer = new LogStreamBatchWriterImpl(mockLog);

        writeBuffer = new UnsafeBuffer(new byte[1024]);
    }

    @Test
    public void shouldWriteSingleEvent()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        final long position = writer
            .event()
                .key(3L)
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .tryWrite();

        assertThat(position).isEqualTo(position(PARTITION_ID, PARTITION_OFFSET));

        assertThat(writeBuffer.getLong(positionOffset(messageOffset(0)))).isEqualTo(position(PARTITION_ID, PARTITION_OFFSET));

        final byte[] valueBuffer = new byte[EVENT_VALUE_1.length];
        writeBuffer.getBytes(valueOffset(messageOffset(0), (short) 0), valueBuffer);
        assertThat(valueBuffer).isEqualTo(EVENT_VALUE_1);
    }

    @Test
    public void shouldWriteEvents()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        final long position = writer
            .event()
                .key(1L)
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .event()
                .key(2L)
                .value(EVENT_VALUE_BUFFER_2)
                .done()
            .tryWrite();

        int offset = 0;

        assertThat(writeBuffer.getLong(positionOffset(messageOffset(offset)))).isEqualTo(position(PARTITION_ID, PARTITION_OFFSET));

        byte[] valueBuffer = new byte[EVENT_VALUE_1.length];
        writeBuffer.getBytes(valueOffset(messageOffset(offset), (short) 0), valueBuffer);
        assertThat(valueBuffer).isEqualTo(EVENT_VALUE_1);

        offset += alignedFramedLength(headerLength(0) + EVENT_VALUE_1.length);

        assertThat(writeBuffer.getLong(positionOffset(messageOffset(offset)))).isEqualTo(position(PARTITION_ID, offset));

        valueBuffer = new byte[EVENT_VALUE_2.length];
        writeBuffer.getBytes(valueOffset(messageOffset(offset), (short) 0), valueBuffer);
        assertThat(valueBuffer).isEqualTo(EVENT_VALUE_2);

        assertThat(position).isEqualTo(position(PARTITION_ID, offset));
    }

    @Test
    public void shouldWriteEventsWithValueBuffer()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        writer
            .event()
                .key(1L)
                .value(EVENT_VALUE_BUFFER_1, 1, 2)
                .done()
            .event()
                .key(2L)
                .value(EVENT_VALUE_BUFFER_2)
                .done()
            .tryWrite();

        int offset = 0;

        byte[] valueBuffer = new byte[2];
        writeBuffer.getBytes(valueOffset(messageOffset(offset), (short) 0), valueBuffer);
        assertThat(valueBuffer).isEqualTo(new byte[] {EVENT_VALUE_1[1], EVENT_VALUE_1[2]});

        offset += alignedFramedLength(headerLength(0) + 2);

        valueBuffer = new byte[EVENT_VALUE_2.length];
        writeBuffer.getBytes(valueOffset(messageOffset(offset), (short) 0), valueBuffer);
        assertThat(valueBuffer).isEqualTo(EVENT_VALUE_2);
    }

    @Test
    public void shouldWriteEventsWthValueWriter()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        writer
            .event()
                .key(1L)
                .valueWriter(new DirectBufferWriter().wrap(EVENT_VALUE_BUFFER_1, 0, EVENT_VALUE_1.length))
                .done()
            .event()
                .key(2L)
                .valueWriter(new DirectBufferWriter().wrap(EVENT_VALUE_BUFFER_2, 0, EVENT_VALUE_2.length))
                .done()
            .tryWrite();

        int offset = 0;

        byte[] valueBuffer = new byte[EVENT_VALUE_1.length];
        writeBuffer.getBytes(valueOffset(messageOffset(offset), (short) 0), valueBuffer);
        assertThat(valueBuffer).isEqualTo(EVENT_VALUE_1);

        offset += alignedFramedLength(headerLength(0) + EVENT_VALUE_1.length);

        valueBuffer = new byte[EVENT_VALUE_2.length];
        writeBuffer.getBytes(valueOffset(messageOffset(offset), (short) 0), valueBuffer);
        assertThat(valueBuffer).isEqualTo(EVENT_VALUE_2);
    }

    @Test
    public void shouldWriteEventsWithMetadataBuffer()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        final int metadata1Offset = 2;
        final int metadata1Length = 3;

        writer
            .event()
                .key(1L)
                .metadata(new UnsafeBuffer(EVENT_METADATA_1), metadata1Offset, metadata1Length)
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .event()
                .key(2L)
                .metadata(new UnsafeBuffer(EVENT_METADATA_2))
                .value(EVENT_VALUE_BUFFER_2)
                .done()
            .tryWrite();

        int offset = 0;

        byte[] metadataBuffer = new byte[metadata1Length];
        writeBuffer.getBytes(metadataOffset(messageOffset(offset)), metadataBuffer);

        final byte[] expectedMetadata1 = Arrays.copyOfRange(EVENT_METADATA_1, metadata1Offset, metadata1Offset + metadata1Length);
        assertThat(metadataBuffer).isEqualTo(expectedMetadata1);

        offset += alignedFramedLength(headerLength(metadata1Length) + EVENT_VALUE_1.length);

        metadataBuffer = new byte[EVENT_METADATA_2.length];
        writeBuffer.getBytes(metadataOffset(messageOffset(offset)), metadataBuffer);
        assertThat(metadataBuffer).isEqualTo(EVENT_METADATA_2);
    }

    @Test
    public void shouldWriteEventsWithMetadataWriter()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        writer
            .event()
                .key(1L)
                .metadataWriter(new DirectBufferWriter().wrap(new UnsafeBuffer(EVENT_METADATA_1), 0, EVENT_METADATA_1.length))
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .event()
                .key(2L)
                .metadataWriter(new DirectBufferWriter().wrap(new UnsafeBuffer(EVENT_METADATA_2), 0, EVENT_METADATA_2.length))
                .value(EVENT_VALUE_BUFFER_2)
                .done()
            .tryWrite();

        int offset = 0;

        byte[] metadataBuffer = new byte[EVENT_METADATA_1.length];
        writeBuffer.getBytes(metadataOffset(messageOffset(offset)), metadataBuffer);
        assertThat(metadataBuffer).isEqualTo(EVENT_METADATA_1);

        offset += alignedFramedLength(headerLength(EVENT_METADATA_1.length) + EVENT_VALUE_1.length);

        metadataBuffer = new byte[EVENT_METADATA_2.length];
        writeBuffer.getBytes(metadataOffset(messageOffset(offset)), metadataBuffer);
        assertThat(metadataBuffer).isEqualTo(EVENT_METADATA_2);
    }

    @Test
    public void shouldWriteEventsWithKey()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        writer
            .event()
                .key(1L)
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .event()
                .key(2L)
                .value(EVENT_VALUE_BUFFER_2)
                .done()
            .tryWrite();

        int offset = 0;

        assertThat(writeBuffer.getLong(keyOffset(messageOffset(offset)))).isEqualTo(1L);

        offset += alignedFramedLength(headerLength(0) + EVENT_VALUE_1.length);

        assertThat(writeBuffer.getLong(keyOffset(messageOffset(offset)))).isEqualTo(2L);
    }

    @Test
    public void shouldWriteEventsWithPositionAsKey()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        writer
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_BUFFER_2)
                .done()
            .tryWrite();

        int offset = 0;

        final long positionEvent1 = position(PARTITION_ID, PARTITION_OFFSET);
        assertThat(writeBuffer.getLong(positionOffset(messageOffset(offset)))).isEqualTo(positionEvent1);
        assertThat(writeBuffer.getLong(keyOffset(messageOffset(offset)))).isEqualTo(positionEvent1);

        offset += alignedFramedLength(headerLength(0) + EVENT_VALUE_1.length);

        final long positionEvent2 = position(PARTITION_ID, offset);
        assertThat(writeBuffer.getLong(positionOffset(messageOffset(offset)))).isEqualTo(positionEvent2);
        assertThat(writeBuffer.getLong(keyOffset(messageOffset(offset)))).isEqualTo(positionEvent2);
    }

    @Test
    public void shouldWriteEventsWithRaftTerm()
    {
        final int raftTerm = 123;
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        writer
            .raftTermId(raftTerm)
            .event()
                .key(1L)
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .event()
                .key(2L)
                .value(EVENT_VALUE_BUFFER_2)
                .done()
            .tryWrite();

        int offset = 0;

        assertThat(writeBuffer.getInt(raftTermOffset(messageOffset(offset)))).isEqualTo(raftTerm);

        offset += alignedFramedLength(headerLength(0) + EVENT_VALUE_1.length);

        assertThat(writeBuffer.getInt(raftTermOffset(messageOffset(offset)))).isEqualTo(raftTerm);
    }

    @Test
    public void shouldWriteEventsWithSourceEvent()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        writer
            .sourceEvent(PARTITION_ID, 99L)
            .event()
                .key(1L)
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .event()
                .key(2L)
                .value(EVENT_VALUE_BUFFER_2)
                .done()
            .tryWrite();

        int offset = 0;

        assertThat(writeBuffer.getInt(sourceEventLogStreamPartitionIdOffset(messageOffset(offset)))).isEqualTo(PARTITION_ID);
        assertThat(writeBuffer.getLong(sourceEventPositionOffset(messageOffset(offset)))).isEqualTo(99L);

        offset += alignedFramedLength(headerLength(0) + EVENT_VALUE_1.length);

        assertThat(writeBuffer.getInt(sourceEventLogStreamPartitionIdOffset(messageOffset(offset)))).isEqualTo(PARTITION_ID);
        assertThat(writeBuffer.getLong(sourceEventPositionOffset(messageOffset(offset)))).isEqualTo(99L);
    }

    @Test
    public void shouldWriteEventsWithoutSourceEvent()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        writer
            .event()
                .key(1L)
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .event()
                .key(2L)
                .value(EVENT_VALUE_BUFFER_2)
                .done()
            .tryWrite();

        int offset = 0;

        assertThat(writeBuffer.getInt(sourceEventLogStreamPartitionIdOffset(messageOffset(offset)))).isEqualTo(-1);
        assertThat(writeBuffer.getLong(sourceEventPositionOffset(messageOffset(offset)))).isEqualTo(-1);

        offset += alignedFramedLength(headerLength(0) + EVENT_VALUE_1.length);

        assertThat(writeBuffer.getInt(sourceEventLogStreamPartitionIdOffset(messageOffset(offset)))).isEqualTo(-1);
        assertThat(writeBuffer.getLong(sourceEventPositionOffset(messageOffset(offset)))).isEqualTo(-1);
    }

    @Test
    public void shouldWriteEventsWithProducerId()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        writer
            .producerId(9)
            .event()
                .key(1L)
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .event()
                .key(2L)
                .value(EVENT_VALUE_BUFFER_2)
                .done()
            .tryWrite();

        int offset = 0;

        assertThat(writeBuffer.getInt(producerIdOffset(messageOffset(offset)))).isEqualTo(9);

        offset += alignedFramedLength(headerLength(0) + EVENT_VALUE_1.length);

        assertThat(writeBuffer.getInt(producerIdOffset(messageOffset(offset)))).isEqualTo(9);
    }

    @Test
    public void shouldRetryIfFailToClaimBatchOnPaddingAtPartitionEnd()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt()))
            .thenReturn((long) LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION)
            .thenAnswer(claimFragment());

        final long position = writer
            .event()
                .key(1L)
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .event()
                .key(2L)
                .value(EVENT_VALUE_BUFFER_2)
                .done()
            .tryWrite();

        int offset = 0;

        assertThat(writeBuffer.getLong(positionOffset(messageOffset(offset)))).isEqualTo(position(PARTITION_ID, PARTITION_OFFSET));

        offset += alignedFramedLength(headerLength(0) + EVENT_VALUE_1.length);

        assertThat(writeBuffer.getLong(positionOffset(messageOffset(offset)))).isEqualTo(position(PARTITION_ID, offset));

        assertThat(position).isEqualTo(position(PARTITION_ID, offset));
    }

    @Test
    public void shouldNotRetryIfFailToClaimBatchOnPartitionEnd()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt()))
            .thenReturn((long) LogBufferAppender.RESULT_END_OF_PARTITION)
            .thenAnswer(claimFragment());

        final long position = writer
                .event()
                    .key(1L)
                    .value(EVENT_VALUE_BUFFER_1)
                    .done()
                .event()
                    .key(2L)
                    .value(EVENT_VALUE_BUFFER_2)
                    .done()
                .tryWrite();

        assertThat(position).isEqualTo(-1);
    }

    @Test
    public void shouldFailToWriteEventsWithoutValue()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("value must not be null");

        writer
            .event()
                .key(1L)
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .event()
                .key(2L)
                .done()
            .tryWrite();
    }

    @Test
    public void shouldFailToWriteEventsWithoutKey()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("key must be greater than or equal to 0");

        writer
            .event()
                .key(1L)
                .value(EVENT_VALUE_BUFFER_1)
                .done()
            .event()
                .value(EVENT_VALUE_BUFFER_2)
                .done()
            .tryWrite();
    }

    @Test
    public void shouldFailToWriteBatchWithoutEvents()
    {
        when(mockWriteBuffer.claim(any(ClaimedFragmentBatch.class), anyInt(), anyInt())).thenAnswer(claimFragment());

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("event count must be greater than 0");

        writer
            .tryWrite();
    }

    private Answer<Long> claimFragment()
    {
        return invocation ->
        {
            final ClaimedFragmentBatch claimedBatch = (ClaimedFragmentBatch) invocation.getArguments()[0];
            final int fragmentCount = (int) invocation.getArguments()[1];
            final int length = (int) invocation.getArguments()[2];

            final int batchLength = length + fragmentCount * (HEADER_LENGTH + FRAME_ALIGNMENT) + FRAME_ALIGNMENT;
            claimedBatch.wrap(writeBuffer, PARTITION_ID, PARTITION_OFFSET, alignedFramedLength(batchLength), () ->
            { });

            return Long.valueOf(alignedFramedLength(length));
        };
    }

}
