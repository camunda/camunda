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
package org.camunda.tngp.broker.transport.controlmessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.transport.clientapi.MockDispatcherFactory.dispatcherOn;
import static org.camunda.tngp.util.VarDataUtil.readBytes;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.protocol.clientapi.ControlMessageResponseDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.util.buffer.DirectBufferWriter;
import org.junit.Before;
import org.junit.Test;

public class ControlMessageResponseWriterTest
{
    private static final byte[] DATA = "eventType".getBytes();

    private final UnsafeBuffer sendBuffer = new UnsafeBuffer(new byte[1024 * 1024]);

    private final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseProtocolHeaderDescriptor protocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ControlMessageResponseDecoder responseDecoder = new ControlMessageResponseDecoder();

    private ControlMessageResponseWriter responseWriter;

    private BrokerEventMetadata metadata;
    private DirectBufferWriter dataWriter;

    @Before
    public void setup()
    {
        metadata = new BrokerEventMetadata();
        dataWriter = new DirectBufferWriter();
    }

    @Test
    public void shouldWriteResponse()
    {
        // given
        final Dispatcher mockDispatcher = dispatcherOn(1, sendBuffer).thatDoes().claim().done();
        responseWriter = new ControlMessageResponseWriter(mockDispatcher);

        // when
        metadata
            .reqChannelId(1)
            .reqConnectionId(2L)
            .reqRequestId(3L);

        dataWriter.wrap(new UnsafeBuffer(DATA), 0, DATA.length);

        responseWriter
            .brokerEventMetadata(metadata)
            .dataWriter(dataWriter)
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

        assertThat(responseDecoder.dataLength()).isEqualTo(DATA.length);

        final byte[] data = readBytes(responseDecoder::getData, responseDecoder::dataLength);
        assertThat(data).isEqualTo(DATA);
    }

    @Test
    public void shouldRetryClaimFragmentIfPadding()
    {
        // given
        final Dispatcher mockDispatcher = dispatcherOn(1, sendBuffer).thatDoes().padding().then().claim().done();
        responseWriter = new ControlMessageResponseWriter(mockDispatcher);

        // when
        metadata
            .reqChannelId(1)
            .reqConnectionId(1L)
            .reqRequestId(2L);

        dataWriter.wrap(new UnsafeBuffer(DATA), 0, DATA.length);

        final boolean isSent = responseWriter
            .brokerEventMetadata(metadata)
            .dataWriter(dataWriter)
            .tryWriteResponse();

        // then
        assertThat(isSent).isTrue();
    }

    @Test
    public void shouldFailIfCannotClaimFragment()
    {
        // given
        final Dispatcher mockDispatcher = dispatcherOn(1, sendBuffer).thatDoes().fail().done();
        responseWriter = new ControlMessageResponseWriter(mockDispatcher);

        // when
        metadata
            .reqChannelId(1)
            .reqConnectionId(1L)
            .reqRequestId(2L);

        dataWriter.wrap(new UnsafeBuffer(DATA), 0, DATA.length);

        final boolean isSent = responseWriter
            .brokerEventMetadata(metadata)
            .dataWriter(dataWriter)
            .tryWriteResponse();

        // then
        assertThat(isSent).isFalse();
    }

}
