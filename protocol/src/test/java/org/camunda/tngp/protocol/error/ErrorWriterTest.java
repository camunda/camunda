package org.camunda.tngp.protocol.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ErrorWriterTest
{

    @Test
    public void testWriting()
    {
        // given
        final ErrorWriter writer = new ErrorWriter();
        final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);

        writer
            .componentCode(123)
            .detailCode(456)
            .errorMessage("an error message");

        // when
        writer.write(buffer, 42);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final ErrorDecoder bodyDecoder = new ErrorDecoder();

        headerDecoder.wrap(buffer, 42);

        assertThat(headerDecoder.blockLength()).isEqualTo(ErrorDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.templateId()).isEqualTo(ErrorDecoder.TEMPLATE_ID);
        assertThat(headerDecoder.schemaId()).isEqualTo(ErrorDecoder.SCHEMA_ID);
        assertThat(headerDecoder.version()).isEqualTo(ErrorDecoder.SCHEMA_VERSION);

        bodyDecoder.wrap(buffer, 42 + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(bodyDecoder.componentCode()).isEqualTo(123);
        assertThat(bodyDecoder.detailCode()).isEqualTo(456);
        assertThat(bodyDecoder.errorMessage()).isEqualTo("an error message");
    }

    @Test
    public void testGetEncodedLength()
    {
        // given
        final ErrorWriter writer = new ErrorWriter();

        final String errorMessage = "an error message";

        writer
            .componentCode(123)
            .detailCode(456)
            .errorMessage(errorMessage);

        final byte[] errorMessageBytes = errorMessage.getBytes(StandardCharsets.UTF_8);

        // when
        final int encodedLength = writer.getLength();

        // then
        assertThat(encodedLength).isEqualTo(
            MessageHeaderEncoder.ENCODED_LENGTH +
            ErrorEncoder.BLOCK_LENGTH +
            ErrorEncoder.errorMessageHeaderLength() +
            errorMessageBytes.length
        );
    }
}
