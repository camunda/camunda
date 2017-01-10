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
package org.camunda.tngp.broker.transport.clientapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandResponseDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.util.buffer.DirectBufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class CommandResponseWriterTest
{
    private static final int TOPIC_ID = 1;
    private static final long KEY = 2L;
    private static final byte[] EVENT = "event".getBytes();

    private final UnsafeBuffer sendBuffer = new UnsafeBuffer(new byte[1024 * 1024]);

    private final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseProtocolHeaderDescriptor protocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();

    private Dispatcher mockSendBuffer;
    private CommandResponseWriter responseWriter;

    private BrokerEventMetadata metadata;
    private DirectBufferWriter eventWriter;

    private ClaimedFragment claimedFragment;

    @Before
    public void setup()
    {
        mockSendBuffer = mock(Dispatcher.class);

        responseWriter = new CommandResponseWriter(mockSendBuffer);

        metadata = new BrokerEventMetadata();
        eventWriter = new DirectBufferWriter();
    }

    @Test
    public void shouldWriteResponse()
    {
        // when
        when(mockSendBuffer.claim(any(ClaimedFragment.class), anyInt(), anyInt())).thenAnswer(claimFragment(0));

        metadata
            .reqConnectionId(1L)
            .reqRequestId(2L);

        eventWriter.wrap(new UnsafeBuffer(EVENT), 0, EVENT.length);

        responseWriter
            .topicId(TOPIC_ID)
            .longKey(KEY)
            .brokerEventMetadata(metadata)
            .eventWriter(eventWriter)
            .tryWriteResponse();

        // then
        int offset = claimedFragment.getOffset();

        transportHeaderDescriptor.wrap(sendBuffer, offset);
        assertThat(transportHeaderDescriptor.protocolId()).isEqualTo(Protocols.REQUEST_RESPONSE);

        offset += TransportHeaderDescriptor.HEADER_LENGTH;

        protocolHeaderDescriptor.wrap(sendBuffer, offset);
        assertThat(protocolHeaderDescriptor.connectionId()).isEqualTo(1L);
        assertThat(protocolHeaderDescriptor.requestId()).isEqualTo(2L);

        offset += RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH;

        messageHeaderDecoder.wrap(sendBuffer, offset);
        assertThat(messageHeaderDecoder.blockLength()).isEqualTo(responseDecoder.sbeBlockLength());
        assertThat(messageHeaderDecoder.templateId()).isEqualTo(responseDecoder.sbeTemplateId());
        assertThat(messageHeaderDecoder.schemaId()).isEqualTo(responseDecoder.sbeSchemaId());
        assertThat(messageHeaderDecoder.version()).isEqualTo(responseDecoder.sbeSchemaVersion());

        offset += messageHeaderDecoder.encodedLength();

        responseDecoder.wrap(sendBuffer, offset, responseDecoder.sbeBlockLength(), responseDecoder.sbeSchemaVersion());
        assertThat(responseDecoder.topicId()).isEqualTo(1);
        assertThat(responseDecoder.longKey()).isEqualTo(2L);
        assertThat(responseDecoder.bytesKeyLength()).isEqualTo(0);
        assertThat(responseDecoder.bytesKey()).isEmpty();

        assertThat(responseDecoder.eventLength()).isEqualTo(EVENT.length);

        final byte[] event = new byte[responseDecoder.eventLength()];
        responseDecoder.getEvent(event, 0, responseDecoder.eventLength());
        assertThat(event).isEqualTo(EVENT);
    }

    @Test
    public void shouldRetryClaimFragmentIfPadding()
    {
        // when
        when(mockSendBuffer.claim(any(ClaimedFragment.class), anyInt(), anyInt())).thenAnswer(new Answer<Long>()
        {
            int invocationCount = 0;

            @Override
            public Long answer(InvocationOnMock invocation) throws Throwable
            {
                invocationCount += 1;
                if (invocationCount == 1)
                {
                    return (long) RESULT_PADDING_AT_END_OF_PARTITION;
                }
                else
                {
                    return claimFragment(0).answer(invocation);
                }
            }
        });

        metadata
            .reqConnectionId(1L)
            .reqRequestId(2L);

        eventWriter.wrap(new UnsafeBuffer(EVENT), 0, EVENT.length);

        final boolean isSent = responseWriter
            .topicId(TOPIC_ID)
            .longKey(KEY)
            .brokerEventMetadata(metadata)
            .eventWriter(eventWriter)
            .tryWriteResponse();

        // then
        assertThat(isSent).isTrue();

        verify(mockSendBuffer, times(2)).claim(any(ClaimedFragment.class), anyInt(), anyInt());
    }

    @Test
    public void shouldFailIfCannotClaimFragment()
    {
        // when
        when(mockSendBuffer.claim(any(ClaimedFragment.class), anyInt(), anyInt())).thenReturn(-1L);

        metadata
            .reqConnectionId(1L)
            .reqRequestId(2L);

        eventWriter.wrap(new UnsafeBuffer(EVENT), 0, EVENT.length);

        final boolean isSent = responseWriter
            .topicId(TOPIC_ID)
            .longKey(KEY)
            .brokerEventMetadata(metadata)
            .eventWriter(eventWriter)
            .tryWriteResponse();

        // then
        assertThat(isSent).isFalse();
    }

    protected Answer<Long> claimFragment(final long offset)
    {
        return invocation ->
        {
            claimedFragment = (ClaimedFragment) invocation.getArguments()[0];
            final int length = (int) invocation.getArguments()[1];

            claimedFragment.wrap(sendBuffer, 0, alignedLength(length));

            final long claimedPosition = offset + alignedLength(length);
            return claimedPosition;
        };
    }

}
