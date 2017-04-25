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
import static org.camunda.tngp.broker.transport.clientapi.MockDispatcherFactory.dispatcherOn;
import static org.camunda.tngp.util.StringUtil.getBytes;
import static org.camunda.tngp.util.VarDataUtil.readBytes;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandResponseDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.util.buffer.DirectBufferWriter;
import org.junit.Before;
import org.junit.Test;

public class CommandResponseWriterTest
{
    private static final int TOPIC_ID = 1;
    private static final long KEY = 2L;
    private static final byte[] EVENT = getBytes("eventType");

    private final UnsafeBuffer sendBuffer = new UnsafeBuffer(new byte[1024 * 1024]);

    private final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseProtocolHeaderDescriptor protocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();

    private CommandResponseWriter responseWriter;

    private BrokerEventMetadata metadata;
    private DirectBufferWriter eventWriter;

    @Before
    public void setup()
    {
        metadata = new BrokerEventMetadata();
        eventWriter = new DirectBufferWriter();
    }

    @Test
    public void shouldWriteResponse()
    {
        // given
        final Dispatcher mockDispatcher = dispatcherOn(1, sendBuffer).thatDoes().claim().done();
        responseWriter = new CommandResponseWriter(mockDispatcher);

        // when
        metadata
            .reqChannelId(1)
            .reqConnectionId(2L)
            .reqRequestId(3L);

        eventWriter.wrap(new UnsafeBuffer(EVENT), 0, EVENT.length);

        responseWriter
            .topicId(TOPIC_ID)
            .key(KEY)
            .brokerEventMetadata(metadata)
            .eventWriter(eventWriter)
            .tryWriteResponse();

        // then
        int offset = DataFrameDescriptor.HEADER_LENGTH;

        transportHeaderDescriptor.wrap(sendBuffer, offset);
        assertThat(transportHeaderDescriptor.protocolId()).isEqualTo(Protocols.REQUEST_RESPONSE);

        offset += TransportHeaderDescriptor.HEADER_LENGTH;

        protocolHeaderDescriptor.wrap(sendBuffer, offset);
        assertThat(protocolHeaderDescriptor.connectionId()).isEqualTo(2L);
        assertThat(protocolHeaderDescriptor.requestId()).isEqualTo(3L);

        offset += RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH;

        messageHeaderDecoder.wrap(sendBuffer, offset);
        assertThat(messageHeaderDecoder.blockLength()).isEqualTo(responseDecoder.sbeBlockLength());
        assertThat(messageHeaderDecoder.templateId()).isEqualTo(responseDecoder.sbeTemplateId());
        assertThat(messageHeaderDecoder.schemaId()).isEqualTo(responseDecoder.sbeSchemaId());
        assertThat(messageHeaderDecoder.version()).isEqualTo(responseDecoder.sbeSchemaVersion());

        offset += messageHeaderDecoder.encodedLength();

        responseDecoder.wrap(sendBuffer, offset, responseDecoder.sbeBlockLength(), responseDecoder.sbeSchemaVersion());
        assertThat(responseDecoder.topicId()).isEqualTo(1);
        assertThat(responseDecoder.key()).isEqualTo(2L);

        assertThat(responseDecoder.eventLength()).isEqualTo(EVENT.length);

        final byte[] event = readBytes(responseDecoder::getEvent, responseDecoder::eventLength);
        assertThat(event).isEqualTo(EVENT);
    }

    @Test
    public void shouldRetryClaimFragmentIfPadding()
    {
        // given
        final Dispatcher mockDispatcher = dispatcherOn(1, sendBuffer).thatDoes().padding().then().claim().done();
        responseWriter = new CommandResponseWriter(mockDispatcher);

        // when
        metadata
            .reqChannelId(1)
            .reqConnectionId(1L)
            .reqRequestId(2L);

        eventWriter.wrap(new UnsafeBuffer(EVENT), 0, EVENT.length);

        final boolean isSent = responseWriter
            .topicId(TOPIC_ID)
            .key(KEY)
            .brokerEventMetadata(metadata)
            .eventWriter(eventWriter)
            .tryWriteResponse();

        // then
        assertThat(isSent).isTrue();
    }

    @Test
    public void shouldFailIfCannotClaimFragment()
    {
        // given
        final Dispatcher mockDispatcher = dispatcherOn(1, sendBuffer).thatDoes().fail().done();
        responseWriter = new CommandResponseWriter(mockDispatcher);

        // when
        metadata
            .reqChannelId(1)
            .reqConnectionId(1L)
            .reqRequestId(2L);

        eventWriter.wrap(new UnsafeBuffer(EVENT), 0, EVENT.length);

        final boolean isSent = responseWriter
            .topicId(TOPIC_ID)
            .key(KEY)
            .brokerEventMetadata(metadata)
            .eventWriter(eventWriter)
            .tryWriteResponse();

        // then
        assertThat(isSent).isFalse();
    }

}
