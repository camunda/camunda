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
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.util.buffer.DirectBufferWriter;

public class CommandResponseWriterTest
{
    public static final String TOPIC_NAME = "test-topic";
    private static final DirectBuffer TOPIC_NAME_BUFFER = wrapString(TOPIC_NAME);
    private static final int PARTITION_ID = 1;
    private static final long KEY = 2L;
    private static final byte[] EVENT = getBytes("eventType");

    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();

    private CommandResponseWriter responseWriter;
    private DirectBufferWriter eventWriter;

    @Before
    public void setup()
    {
        eventWriter = new DirectBufferWriter();
    }

    @Test
    public void shouldWriteResponse()
    {
        // given
        responseWriter = new CommandResponseWriter(null);

        eventWriter.wrap(new UnsafeBuffer(EVENT), 0, EVENT.length);

        responseWriter
            .topicName(TOPIC_NAME_BUFFER)
            .partitionId(PARTITION_ID)
            .key(KEY)
            .eventWriter(eventWriter);

        final UnsafeBuffer buf = new UnsafeBuffer(new byte[responseWriter.getLength()]);

        // when
        responseWriter.write(buf, 0);

        // then
        int offset = 0;

        messageHeaderDecoder.wrap(buf, offset);
        assertThat(messageHeaderDecoder.blockLength()).isEqualTo(responseDecoder.sbeBlockLength());
        assertThat(messageHeaderDecoder.templateId()).isEqualTo(responseDecoder.sbeTemplateId());
        assertThat(messageHeaderDecoder.schemaId()).isEqualTo(responseDecoder.sbeSchemaId());
        assertThat(messageHeaderDecoder.version()).isEqualTo(responseDecoder.sbeSchemaVersion());

        offset += messageHeaderDecoder.encodedLength();

        responseDecoder.wrap(buf, offset, responseDecoder.sbeBlockLength(), responseDecoder.sbeSchemaVersion());
        assertThat(responseDecoder.topicName()).isEqualTo(TOPIC_NAME);
        assertThat(responseDecoder.partitionId()).isEqualTo(PARTITION_ID);
        assertThat(responseDecoder.key()).isEqualTo(2L);

        assertThat(responseDecoder.eventLength()).isEqualTo(EVENT.length);

        final byte[] event = readBytes(responseDecoder::getEvent, responseDecoder::eventLength);
        assertThat(event).isEqualTo(EVENT);
    }
}
