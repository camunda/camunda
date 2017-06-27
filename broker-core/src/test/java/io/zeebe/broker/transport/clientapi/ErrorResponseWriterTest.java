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
