/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.event.request.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EventFinderTest
{
    protected static final byte[] EVENT1 = "event1".getBytes();
    protected static final byte[] EVENT2 = "event2".getBytes();

    protected static final int BUFFER_SIZE = (EVENT1.length + EVENT2.length) * 2;

    protected StubLogReader logReader;

    @Mock
    protected Log log;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logReader = new StubLogReader(log)
                .addEntry(createMockEventWriter(EVENT1))
                .addEntry(createMockEventWriter(EVENT2));
    }

    @Test
    public void shouldFindEvents()
    {
        // given
        final EventFinder eventFinder = new EventFinder(logReader, BUFFER_SIZE);
        eventFinder.init(log, 0, 2);

        // when
        final int eventCount = eventFinder.findEvents();

        // then
        assertThat(eventCount).isEqualTo(2);
        assertThat(eventFinder.getEventPosition(0)).isEqualTo(logReader.getEntryPosition(0));
        assertThat(eventFinder.getEventPosition(1)).isEqualTo(logReader.getEntryPosition(1));

        final DirectBuffer eventBuffer = eventFinder.getEventBuffer();
        final byte[] event1 = new byte[EVENT1.length];
        final byte[] event2 = new byte[EVENT2.length];

        final int offsetEvent1 = eventFinder.getEventBufferOffset(0);
        assertThat(offsetEvent1).isEqualTo(0);
        assertThat(eventFinder.getEventBufferLength(0)).isEqualTo(EVENT1.length);

        eventBuffer.getBytes(offsetEvent1, event1);
        assertThat(event1).isEqualTo(EVENT1);

        final int offsetEvent2 = eventFinder.getEventBufferOffset(1);
        assertThat(offsetEvent2).isEqualTo(EVENT1.length);
        assertThat(eventFinder.getEventBufferLength(1)).isEqualTo(EVENT2.length);

        eventBuffer.getBytes(offsetEvent2, event2);
        assertThat(event2).isEqualTo(EVENT2);
    }

    @Test
    public void shouldFindNotMoreThanMaxEvents()
    {
        // given
        final EventFinder eventFinder = new EventFinder(logReader, BUFFER_SIZE);
        eventFinder.init(log, 0, 1);

        // when
        final int eventCount = eventFinder.findEvents();

        // then
        assertThat(eventCount).isEqualTo(1);
        assertThat(eventFinder.getEventPosition(0)).isEqualTo(logReader.getEntryPosition(0));

        assertThat(eventFinder.getEventBufferOffset(0)).isEqualTo(0);
        assertThat(eventFinder.getEventBufferLength(0)).isEqualTo(EVENT1.length);

        final DirectBuffer eventBuffer = eventFinder.getEventBuffer();
        final byte[] event = new byte[EVENT1.length];

        eventBuffer.getBytes(0, event);
        assertThat(event).isEqualTo(EVENT1);
    }

    @Test
    public void shouldFindNoEventsIfNotAvailable()
    {
        // given
        final EventFinder eventFinder = new EventFinder(logReader, BUFFER_SIZE);
        eventFinder.init(log, logReader.getTailPosition(), 1);

        // when
        final int eventCount = eventFinder.findEvents();

        // then
        assertThat(eventCount).isEqualTo(0);
    }

    @Test
    public void shouldNotReadMoreThanBufferSize()
    {
        // given
        logReader
            .addEntry(createMockEventWriter(new byte[BUFFER_SIZE]))
            .addEntry(createMockEventWriter("event after reaches buffer size".getBytes()));

        final EventFinder eventFinder = new EventFinder(logReader, BUFFER_SIZE);
        eventFinder.init(log, 0, 4);

        // when
        final int eventCount = eventFinder.findEvents();

        // then
        assertThat(eventCount).isEqualTo(2);
    }

    protected BufferWriter createMockEventWriter(byte[] event)
    {
        return new BufferWriter()
        {

            @Override
            public void write(MutableDirectBuffer buffer, int offset)
            {
                buffer.wrap(event);
            }

            @Override
            public int getLength()
            {
                return event.length;
            }
        };
    }

}
