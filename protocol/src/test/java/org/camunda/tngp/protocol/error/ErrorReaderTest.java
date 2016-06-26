package org.camunda.tngp.protocol.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class ErrorReaderTest
{
    public int writeSbeEncodedRequestBuffer(final MutableDirectBuffer buffer, final int offset,
            final int componentCode, final int detailCode, final String errorMessage)
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final ErrorEncoder bodyEncoder = new ErrorEncoder();

        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(123)
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion())
            .shardId(456)
            .templateId(bodyEncoder.sbeTemplateId());


        final byte[] bytes = errorMessage.getBytes(StandardCharsets.UTF_8);

        bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength())
            .componentCode(componentCode)
            .detailCode(detailCode)
            .putErrorMessage(bytes, 0, bytes.length);

        return headerEncoder.encodedLength() + bodyEncoder.encodedLength();
    }

    @Test
    public void testReading()
    {
        // given
        final ErrorReader reader = new ErrorReader();
        final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);
        final int encodedLength = writeSbeEncodedRequestBuffer(buffer, 0, 5, 404, "some error");

        // when
        reader.wrap(buffer, 0, encodedLength);

        // then
        assertThat(reader.errorMessage()).isEqualTo("some error");
        assertThat(reader.componentCode()).isEqualTo(5);
        assertThat(reader.detailCode()).isEqualTo(404);
    }

    @Test
    public void testReadingWithOffset()
    {
        // given
        final ErrorReader reader = new ErrorReader();
        final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);
        final int encodedLength = writeSbeEncodedRequestBuffer(buffer, 123, 7, 404, "some error");

        // when
        reader.wrap(buffer, 123, encodedLength);

        // then
        assertThat(reader.errorMessage()).isEqualTo("some error");
        assertThat(reader.componentCode()).isEqualTo(7);
        assertThat(reader.detailCode()).isEqualTo(404);
    }

    @Test
    public void testReuseInstance()
    {
        // given two buffers
        final ErrorReader reader = new ErrorReader();

        final MutableDirectBuffer buffer1 = new UnsafeBuffer(new byte[512]);
        final int encodedLength1 = writeSbeEncodedRequestBuffer(buffer1, 0, 8, 404, "some error");
        final MutableDirectBuffer buffer2 = new UnsafeBuffer(new byte[512]);
        final int encodedLength2 = writeSbeEncodedRequestBuffer(buffer2, 0, 9, 405, "some other error");

        // and the first buffer has already been read
        reader.wrap(buffer1, 0, encodedLength1);
        reader.errorMessage();

        // when we read the second buffer
        reader.wrap(buffer2, 0, encodedLength2);

        // then the resource is returned correctly
        assertThat(reader.errorMessage()).isEqualTo("some other error");
        assertThat(reader.componentCode()).isEqualTo(9);
        assertThat(reader.detailCode()).isEqualTo(405);
    }
}
