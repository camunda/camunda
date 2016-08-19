package org.camunda.tngp.protocol.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

import org.agrona.concurrent.UnsafeBuffer;

public class SingleTaskAckResponseReaderTest
{
    protected UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);
    protected int writtenLength;

    @Before
    public void writeToBuffer()
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final SingleTaskAckEncoder bodyEncoder = new SingleTaskAckEncoder();

        headerEncoder.wrap(buffer, 76)
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(123)
            .schemaId(bodyEncoder.sbeSchemaId())
            .shardId(987)
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(buffer, 76 + headerEncoder.encodedLength())
            .taskId(144L);

        writtenLength = headerEncoder.encodedLength() + bodyEncoder.encodedLength();
    }

    @Test
    public void shouldReadFromBuffer()
    {
        // given
        final SingleTaskAckResponseReader reader = new SingleTaskAckResponseReader();

        // when
        reader.wrap(buffer, 76, writtenLength);

        // then
        assertThat(reader.taskId()).isEqualTo(144L);
    }
}
