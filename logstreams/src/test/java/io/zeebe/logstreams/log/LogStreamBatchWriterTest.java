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

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.util.*;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.DirectBufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.*;
import org.junit.rules.*;

public class LogStreamBatchWriterTest
{
    private static final DirectBuffer EVENT_VALUE_1 = wrapString("foo");
    private static final DirectBuffer EVENT_VALUE_2 = wrapString("bar");
    private static final DirectBuffer EVENT_METADATA_1 = wrapString("foobar");
    private static final DirectBuffer EVENT_METADATA_2 = wrapString("baz");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    public LogStreamRule logStreamRule = new LogStreamRule(temporaryFolder);
    public LogStreamReaderRule readerRule = new LogStreamReaderRule(logStreamRule);
    public LogStreamWriterRule writerRule = new LogStreamWriterRule(logStreamRule);

    @Rule
    public RuleChain ruleChain =
        RuleChain.outerRule(temporaryFolder)
                 .around(logStreamRule)
                 .around(writerRule)
                 .around(readerRule);

    private LogStreamBatchWriter writer;

    @Before
    public void setUp()
    {
        writer = new LogStreamBatchWriterImpl(logStreamRule.getLogStream());

        logStreamRule.setCommitPosition(Long.MAX_VALUE);
    }

    private List<LoggedEvent> getWrittenEvents(long position)
    {
        final List<LoggedEvent> events = new ArrayList<>();

        assertThat(position).isGreaterThan(0);

        writerRule.waitForPositionToBeAppended(position);

        long eventPosition = -1L;

        while (eventPosition < position)
        {
            final LoggedEventImpl event = (LoggedEventImpl) readerRule.nextEvent();

            final LoggedEventImpl eventCopy = new LoggedEventImpl();
            final DirectBuffer bufferCopy = BufferUtil.cloneBuffer(event.getBuffer());

            eventCopy.wrap(bufferCopy, event.getFragmentOffset());
            events.add(eventCopy);

            eventPosition = event.getPosition();
        }

        assertThat(eventPosition)
            .withFailMessage("No written event found at position: {}", position)
            .isEqualTo(position);

        return events;
    }

    private DirectBuffer getValueBuffer(LoggedEvent event)
    {
        final DirectBuffer buffer = event.getValueBuffer();
        final int offset = event.getValueOffset();
        final int length = event.getValueLength();

        return new UnsafeBuffer(buffer, offset, length);
    }

    private DirectBuffer getMetadataBuffer(LoggedEvent event)
    {
        final DirectBuffer buffer = event.getMetadata();
        final int offset = event.getMetadataOffset();
        final int length = event.getMetadataLength();

        return new UnsafeBuffer(buffer, offset, length);
    }

    @Test
    public void shouldReturnPositionOfSingleEvent()
    {
        // when
        final long position = writer
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1)
                .done()
            .tryWrite();

        // then
        assertThat(position).isGreaterThan(0);

        final List<LoggedEvent> events = getWrittenEvents(position);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPosition()).isEqualTo(position);
    }

    @Test
    public void shouldReturnPositionOfLastEvent()
    {
        // when
        final long position = writer
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1)
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_2)
                .done()
            .tryWrite();

        // then
        assertThat(position).isGreaterThan(0);

        final List<LoggedEvent> events = getWrittenEvents(position);
        assertThat(events).hasSize(2);
        assertThat(events.get(1).getPosition()).isEqualTo(position);
    }

    @Test
    public void shouldWriteEventWithValueBuffer()
    {
        // when
        final long position = writer
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1)
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_2)
                .done()
            .tryWrite();

        // then
        final List<LoggedEvent> events = getWrittenEvents(position);
        assertThat(getValueBuffer(events.get(0))).isEqualTo(EVENT_VALUE_1);
        assertThat(getValueBuffer(events.get(1))).isEqualTo(EVENT_VALUE_2);
    }

    @Test
    public void shouldWriteEventWithValueBufferPartially()
    {
        // when
        final long position = writer
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1, 1, 2)
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_2, 1, 2)
                .done()
            .tryWrite();

        // then
        final List<LoggedEvent> events = getWrittenEvents(position);
        assertThat(getValueBuffer(events.get(0))).isEqualTo(new UnsafeBuffer(EVENT_VALUE_1, 1, 2));
        assertThat(getValueBuffer(events.get(1))).isEqualTo(new UnsafeBuffer(EVENT_VALUE_2, 1, 2));
    }

    @Test
    public void shouldWriteEventWithValueWriter()
    {
        // when
        final long position = writer
            .event()
                .positionAsKey()
                .valueWriter(new DirectBufferWriter().wrap(EVENT_VALUE_1))
                .done()
            .event()
                .positionAsKey()
                .valueWriter(new DirectBufferWriter().wrap(EVENT_VALUE_2))
                .done()
            .tryWrite();

        // then
        final List<LoggedEvent> events = getWrittenEvents(position);
        assertThat(getValueBuffer(events.get(0))).isEqualTo(EVENT_VALUE_1);
        assertThat(getValueBuffer(events.get(1))).isEqualTo(EVENT_VALUE_2);
    }

    @Test
    public void shouldWriteEventWithMetadataBuffer()
    {
        // when
        final long position = writer
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1)
                .metadata(EVENT_METADATA_1)
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_2)
                .metadata(EVENT_METADATA_2)
                .done()
            .tryWrite();

        // then
        final List<LoggedEvent> events = getWrittenEvents(position);
        assertThat(getMetadataBuffer(events.get(0))).isEqualTo(EVENT_METADATA_1);
        assertThat(getMetadataBuffer(events.get(1))).isEqualTo(EVENT_METADATA_2);
    }

    @Test
    public void shouldWriteEventWithMetadataBufferPartially()
    {
        // when
        final long position = writer
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1)
                .metadata(EVENT_METADATA_1, 1, 2)
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_2)
                .metadata(EVENT_METADATA_2, 1, 2)
                .done()
            .tryWrite();

        // then
        final List<LoggedEvent> events = getWrittenEvents(position);
        assertThat(getMetadataBuffer(events.get(0))).isEqualTo(new UnsafeBuffer(EVENT_METADATA_1, 1, 2));
        assertThat(getMetadataBuffer(events.get(1))).isEqualTo(new UnsafeBuffer(EVENT_METADATA_2, 1, 2));
    }

    @Test
    public void shouldWriteEventWithMetadataWriter()
    {
        // when
        final long position = writer
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1)
                .metadataWriter(new DirectBufferWriter().wrap(EVENT_METADATA_1))
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_2)
                .metadataWriter(new DirectBufferWriter().wrap(EVENT_METADATA_2))
                .done()
            .tryWrite();

        // then
        final List<LoggedEvent> events = getWrittenEvents(position);
        assertThat(getMetadataBuffer(events.get(0))).isEqualTo(EVENT_METADATA_1);
        assertThat(getMetadataBuffer(events.get(1))).isEqualTo(EVENT_METADATA_2);
    }

    @Test
    public void shouldWriteEventWithKey()
    {
        // when
        final long position = writer
            .event()
                .key(123L)
                .value(EVENT_VALUE_1)
                .done()
            .event()
                .key(456L)
                .value(EVENT_VALUE_2)
                .done()
            .tryWrite();

        // then
        assertThat(getWrittenEvents(position))
            .extracting(LoggedEvent::getKey)
            .containsExactly(123L, 456L);
    }

    @Test
    public void shouldWriteEventWithPositionAsKey()
    {
        // when
        final long position = writer
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1)
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_2)
                .done()
            .tryWrite();

        // then
        final List<LoggedEvent> events = getWrittenEvents(position);
        final LoggedEvent firstEvent = events.get(0);
        final long positionEvent1 = firstEvent.getPosition();
        final LoggedEvent secondEvent = events.get(1);
        final long positionEvent2 = secondEvent.getPosition();

        assertThat(positionEvent1).isGreaterThan(0);
        assertThat(positionEvent2).isGreaterThan(0);
        assertThat(positionEvent1).isLessThan(positionEvent2);
        assertThat(firstEvent.getKey()).isEqualTo(firstEvent.getPosition());
        assertThat(secondEvent.getKey()).isEqualTo(secondEvent.getPosition());
    }

    @Test
    public void shouldWriteEventWithSourceEvent()
    {
        // when
        final long position = writer
            .sourceEvent(4, 123L)
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1)
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_2)
                .done()
            .tryWrite();

        // then
        final List<LoggedEvent> events = getWrittenEvents(position);

        assertThat(events.get(0).getSourceEventLogStreamPartitionId()).isEqualTo(4);
        assertThat(events.get(0).getSourceEventPosition()).isEqualTo(123L);

        assertThat(events.get(1).getSourceEventLogStreamPartitionId()).isEqualTo(4);
        assertThat(events.get(1).getSourceEventPosition()).isEqualTo(123L);
    }

    @Test
    public void shouldWriteEventWithoutSourceEvent()
    {
        // when
        final long position = writer
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1)
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_2)
                .done()
            .tryWrite();

        // then
        final List<LoggedEvent> events = getWrittenEvents(position);

        assertThat(events.get(0).getSourceEventLogStreamPartitionId()).isEqualTo(-1);
        assertThat(events.get(0).getSourceEventPosition()).isEqualTo(-1L);

        assertThat(events.get(1).getSourceEventLogStreamPartitionId()).isEqualTo(-1);
        assertThat(events.get(1).getSourceEventPosition()).isEqualTo(-1L);
    }

    @Test
    public void shouldWriteEventWithProducerId()
    {
        // when
        final long position = writer
            .producerId(123)
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1)
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_2)
                .done()
            .tryWrite();

        // then
        assertThat(getWrittenEvents(position))
            .extracting(LoggedEvent::getProducerId)
            .containsExactly(123, 123);
    }

    @Test
    public void shouldWriteEventWithoutProducerId()
    {
        // when
        final long position = writer
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1)
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_2)
                .done()
            .tryWrite();

        // then
        assertThat(getWrittenEvents(position))
            .extracting(LoggedEvent::getProducerId)
            .containsExactly(-1, -1);
    }

    @Test
    public void shouldWriteEventWithRaftTerm()
    {
        // given
        logStreamRule.getLogStream().setTerm(123);

        // when
        final long position = writer
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_1)
                .done()
            .event()
                .positionAsKey()
                .value(EVENT_VALUE_2)
                .done()
            .tryWrite();

        // then
        assertThat(getWrittenEvents(position))
            .extracting(LoggedEvent::getRaftTerm)
            .containsExactly(123, 123);
    }

    @Test
    public void shouldFailToWriteEventWithoutKey()
    {
        // when
        assertThatThrownBy(() ->
        {
            writer
                .event()
                    .positionAsKey()
                    .value(EVENT_VALUE_1)
                    .done()
                .event()
                    .value(EVENT_VALUE_2)
                    .done()
                .tryWrite();
        })
            .isInstanceOf(RuntimeException.class)
            .hasMessage("key must be greater than or equal to 0");
    }

    @Test
    public void shouldFailToWriteEventWithoutValue()
    {
        // when
        assertThatThrownBy(() ->
        {
            writer
                .event()
                    .positionAsKey()
                    .value(EVENT_VALUE_1)
                    .done()
                .event()
                    .positionAsKey()
                    .done()
                .tryWrite();
        })
            .isInstanceOf(RuntimeException.class)
            .hasMessage("value must not be null");
    }

    @Test
    public void shouldFailToWriteBatchWithoutEvents()
    {
        // when
        assertThatThrownBy(() ->
        {
            writer
                .tryWrite();
        })
            .isInstanceOf(RuntimeException.class)
            .hasMessage("event count must be greater than 0");
    }

}
