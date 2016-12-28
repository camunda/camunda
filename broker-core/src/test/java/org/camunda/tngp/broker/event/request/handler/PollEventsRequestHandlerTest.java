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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.test.util.BufferWriterUtil;
import org.camunda.tngp.broker.transport.clientapi.ClientApiMessageHandler;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.protocol.error.ErrorReader;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.event.EventBatchReader;
import org.camunda.tngp.protocol.event.EventBatchWriter;
import org.camunda.tngp.protocol.event.PollEventsEncoder;
import org.camunda.tngp.protocol.event.PollEventsRequestReader;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PollEventsRequestHandlerTest
{
    protected static final byte[] EVENT1 = "event1".getBytes();
    protected static final byte[] EVENT2 = "event2".getBytes();

    @Mock
    protected DirectBuffer requestBuffer;

    @Mock
    protected DeferredResponse response;

    @Mock
    protected PollEventsRequestReader requestReader;

    @Mock
    protected LogStream log;

    @Mock
    protected ClientApiMessageHandler logManager;

    @Mock
    protected DecodedEvent eventContext;

    @Captor
    protected ArgumentCaptor<BufferWriter> captor;

    protected StubLogReader logReader;

    protected PollEventsRequestHandler handler;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        when(logManager.getLogStreamById(anyInt())).thenReturn(log);
        when(eventContext.getLogManager()).thenReturn(logManager);

        logReader = new StubLogReader(log);

        handler = new PollEventsRequestHandler(requestReader, logReader, new EventBatchWriter(), new ErrorWriter());
    }

    @Test
    public void shouldPollEvents()
    {
        // given
        when(requestReader.startPosition()).thenReturn(0L);
        when(requestReader.maxEvents()).thenReturn(10);
        when(requestReader.topicId()).thenReturn(1);

        logReader
            .addEntry(createMockEventWriter(EVENT1))
            .addEntry(createMockEventWriter(EVENT2));

        when(response.allocateAndWrite(any())).thenReturn(true);

        // when
        final long returnValue = handler.onRequest(eventContext, requestBuffer, 0, 124, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final EventBatchReader reader = new EventBatchReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.eventCount()).isEqualTo(2);

        reader.nextEvent();

        assertThat(reader.currentPosition()).isEqualTo(0);
        assertThatBuffer(reader.currentEvent())
            .hasCapacity(EVENT1.length)
            .hasBytes(EVENT1);

        reader.nextEvent();

        assertThat(reader.currentPosition()).isEqualTo(logReader.getEntryPosition(1));
        assertThatBuffer(reader.currentEvent())
            .hasCapacity(EVENT2.length)
            .hasBytes(EVENT2);
    }

    @Test
    public void shouldWriteEmptyResponseIfNoneAvailable()
    {
        // given
        when(requestReader.startPosition()).thenReturn(0L);
        when(requestReader.maxEvents()).thenReturn(10);
        when(requestReader.topicId()).thenReturn(1);

        when(response.allocateAndWrite(any())).thenReturn(true);

        // when
        final long returnValue = handler.onRequest(eventContext, requestBuffer, 0, 124, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final EventBatchReader reader = new EventBatchReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.eventCount()).isEqualTo(0);
    }

    @Test
    public void shouldWriteEmptyResponseIfNoneAvailableAfterStartPosition()
    {
        // given
        when(requestReader.startPosition()).thenReturn(999L);
        when(requestReader.maxEvents()).thenReturn(10);
        when(requestReader.topicId()).thenReturn(1);

        logReader
            .addEntry(createMockEventWriter(EVENT1))
            .addEntry(createMockEventWriter(EVENT2));

        when(response.allocateAndWrite(any())).thenReturn(true);

        // when
        final long returnValue = handler.onRequest(eventContext, requestBuffer, 0, 124, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final EventBatchReader reader = new EventBatchReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.eventCount()).isEqualTo(0);
    }

    @Test
    public void shouldWriteErrorResponseOnMissingStartPosition()
    {
        // given
        when(requestReader.startPosition()).thenReturn(PollEventsEncoder.startPositionNullValue());
        when(requestReader.maxEvents()).thenReturn(10);
        when(requestReader.topicId()).thenReturn(1);

        when(response.allocateAndWrite(any())).thenReturn(true);

        // when
        final long returnValue = handler.onRequest(eventContext, requestBuffer, 0, 124, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final ErrorReader reader = new ErrorReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.componentCode()).isEqualTo(EventErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(EventErrors.POLL_EVENTS_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("start position must be greater or equal to 0");
    }

    @Test
    public void shouldWriteErrorResponseOnMissingMaxEvents()
    {
        // given
        when(requestReader.startPosition()).thenReturn(0L);
        when(requestReader.maxEvents()).thenReturn(PollEventsEncoder.maxEventsNullValue());
        when(requestReader.topicId()).thenReturn(1);

        when(response.allocateAndWrite(any())).thenReturn(true);

        // when
        final long returnValue = handler.onRequest(eventContext, requestBuffer, 0, 124, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final ErrorReader reader = new ErrorReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.componentCode()).isEqualTo(EventErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(EventErrors.POLL_EVENTS_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("max events must be greater than 0");
    }

    @Test
    public void shouldWriteErrorResponseOnMissingTopicId()
    {
        // given
        when(requestReader.startPosition()).thenReturn(0L);
        when(requestReader.maxEvents()).thenReturn(10);
        when(requestReader.topicId()).thenReturn(PollEventsEncoder.topicIdNullValue());

        when(response.allocateAndWrite(any())).thenReturn(true);

        // when
        final long returnValue = handler.onRequest(eventContext, requestBuffer, 0, 124, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final ErrorReader reader = new ErrorReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.componentCode()).isEqualTo(EventErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(EventErrors.POLL_EVENTS_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("topic id must be greater or equal to 0");
    }

    @Test
    public void shouldWriteErrorResponseOnInvalidTopicId()
    {
        // given
        when(requestReader.startPosition()).thenReturn(0L);
        when(requestReader.maxEvents()).thenReturn(10);
        when(requestReader.topicId()).thenReturn(41);

        when(logManager.getLogStreamById(41)).thenReturn(null);
        when(response.allocateAndWrite(any())).thenReturn(true);

        // when
        final long returnValue = handler.onRequest(eventContext, requestBuffer, 0, 124, response);

        // then
        assertThat(returnValue).isGreaterThanOrEqualTo(0L);

        final InOrder inOrder = inOrder(response);
        inOrder.verify(response).allocateAndWrite(captor.capture());
        inOrder.verify(response).commit();
        verifyNoMoreInteractions(response);

        final ErrorReader reader = new ErrorReader();
        BufferWriterUtil.wrap(captor.getValue(), reader);

        assertThat(reader.componentCode()).isEqualTo(EventErrors.COMPONENT_CODE);
        assertThat(reader.detailCode()).isEqualTo(EventErrors.POLL_EVENTS_ERROR);
        assertThat(reader.errorMessage()).isEqualTo("found no topic with id: 41");
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
