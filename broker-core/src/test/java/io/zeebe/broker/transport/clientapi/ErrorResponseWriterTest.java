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
package io.zeebe.broker.transport.clientapi;

import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.VarDataUtil.readBytes;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;

public class ErrorResponseWriterTest
{
    private static final byte[] REQUEST = getBytes("request");
    private static final DirectBuffer REQUEST_BUFFER = new UnsafeBuffer(REQUEST);

    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ErrorResponseDecoder responseDecoder = new ErrorResponseDecoder();

    private ErrorResponseWriter responseWriter;

    @Before
    public void setup()
    {
        responseWriter = new ErrorResponseWriter(null);
    }

    @Test
    public void shouldWriteResponse()
    {
        responseWriter
            .errorCode(ErrorCode.TOPIC_NOT_FOUND)
            .errorMessage("error message")
            .failedRequest(REQUEST_BUFFER, 0, REQUEST_BUFFER.capacity());
        final UnsafeBuffer buf = new UnsafeBuffer(new byte[responseWriter.getLength()]);

        // when
        responseWriter.write(buf, 0);

        // then
        int offset = 0;
        messageHeaderDecoder.wrap(buf, offset);
        assertThat(messageHeaderDecoder.schemaId()).isEqualTo(responseDecoder.sbeSchemaId());
        assertThat(messageHeaderDecoder.version()).isEqualTo(responseDecoder.sbeSchemaVersion());
        assertThat(messageHeaderDecoder.templateId()).isEqualTo(responseDecoder.sbeTemplateId());
        assertThat(messageHeaderDecoder.blockLength()).isEqualTo(responseDecoder.sbeBlockLength());

        offset += messageHeaderDecoder.encodedLength();

        responseDecoder.wrap(buf, offset, responseDecoder.sbeBlockLength(), responseDecoder.sbeSchemaVersion());
        assertThat(responseDecoder.errorCode()).isEqualTo(ErrorCode.TOPIC_NOT_FOUND);
        assertThat(responseDecoder.errorData()).isEqualTo("error message");

        final byte[] failureRequestBuffer = readBytes(responseDecoder::getFailedRequest, responseDecoder::failedRequestLength);

        assertThat(failureRequestBuffer).isEqualTo(REQUEST);
    }
}
