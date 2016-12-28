package org.camunda.tngp.broker.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

import java.nio.charset.StandardCharsets;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.taskqueue.CompleteTaskEncoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.junit.Before;
import org.junit.Test;

public class CompleteTaskRequestReaderTest
{
    protected UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);
    protected int writtenLength;

    protected static final byte[] PAYLOAD = "ayayay".getBytes(StandardCharsets.UTF_8);

    @Before
    public void writeToBuffer()
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final CompleteTaskEncoder bodyEncoder = new CompleteTaskEncoder();

        headerEncoder.wrap(buffer, 76)
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(123)
            .schemaId(bodyEncoder.sbeSchemaId())
            .shardId(987)
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(buffer, 76 + headerEncoder.encodedLength())
            .consumerId(Short.MAX_VALUE)
            .taskId(12354L)
            .putPayload(PAYLOAD, 0, PAYLOAD.length);

        writtenLength = headerEncoder.encodedLength() + bodyEncoder.encodedLength();
    }

    @Test
    public void shouldReadFromBuffer()
    {
        // given
        final CompleteTaskRequestReader reader = new CompleteTaskRequestReader();

        // when
        reader.wrap(buffer, 76, writtenLength);

        // then
        assertThat(reader.consumerId()).isEqualTo(Short.MAX_VALUE);
        assertThat(reader.taskId()).isEqualTo(12354L);
        assertThatBuffer(reader.getPayload()).hasBytes(PAYLOAD);
    }
}
