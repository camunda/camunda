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
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

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
    public void shouldFindEvent()
    {
        // given
        final EventFinder eventFinder = new EventFinder(logReader);
        eventFinder.init(log, 0);

        // when
        eventFinder.findEvents();

        // then
        assertThat(eventFinder.getEventPosition()).isEqualTo(logReader.getEntryPosition(0));

        final EventReader eventFound = eventFinder.getEvent();
        assertThat(eventFound).isNotNull();

        assertThatBuffer(eventFound.getEventBuffer())
            .isNotNull()
            .hasCapacity(EVENT1.length)
            .hasBytes(EVENT1);
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
