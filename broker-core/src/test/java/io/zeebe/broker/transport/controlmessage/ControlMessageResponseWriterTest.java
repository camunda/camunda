package io.zeebe.broker.transport.controlmessage;

import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.VarDataUtil.readBytes;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.zeebe.protocol.clientapi.ControlMessageResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.util.buffer.DirectBufferWriter;

public class ControlMessageResponseWriterTest
{
    private static final byte[] DATA = getBytes("eventType");

    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ControlMessageResponseDecoder responseDecoder = new ControlMessageResponseDecoder();

    private ControlMessageResponseWriter responseWriter;
    private DirectBufferWriter dataWriter;

    @Before
    public void setup()
    {
        dataWriter = new DirectBufferWriter();
    }

    @Test
    public void shouldWriteResponse()
    {
        // given
        responseWriter = new ControlMessageResponseWriter(null);

        dataWriter.wrap(new UnsafeBuffer(DATA), 0, DATA.length);
        responseWriter.dataWriter(dataWriter);

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

        assertThat(responseDecoder.dataLength()).isEqualTo(DATA.length);

        final byte[] data = readBytes(responseDecoder::getData, responseDecoder::dataLength);
        assertThat(data).isEqualTo(DATA);
    }
}
