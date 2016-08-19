package org.camunda.tngp.broker.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.protocol.taskqueue.SingleTaskAckDecoder;
import org.junit.Test;

import org.agrona.concurrent.UnsafeBuffer;

public class SingleTaskAckResponseWriterTest
{
    @Test
    public void shouldWriteToBuffer()
    {
        // given
        final SingleTaskAckResponseWriter writer = new SingleTaskAckResponseWriter();
        writer.taskId(7665L);

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);

        // when
        writer.write(buffer, 50);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(buffer, 50);

        final SingleTaskAckDecoder bodyDecoder = new SingleTaskAckDecoder();
        bodyDecoder.wrap(buffer, 50 + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(headerDecoder.blockLength()).isEqualTo(SingleTaskAckDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.resourceId()).isEqualTo(0);
        assertThat(headerDecoder.schemaId()).isEqualTo(SingleTaskAckDecoder.SCHEMA_ID);
        assertThat(headerDecoder.shardId()).isEqualTo(0);
        assertThat(headerDecoder.templateId()).isEqualTo(SingleTaskAckDecoder.TEMPLATE_ID);
        assertThat(headerDecoder.version()).isEqualTo(SingleTaskAckDecoder.SCHEMA_VERSION);

        assertThat(bodyDecoder.taskId()).isEqualTo(7665L);
    }

    @Test
    public void shouldProvideWriteLength()
    {
        // given
        final SingleTaskAckResponseWriter writer = new SingleTaskAckResponseWriter();
        writer.taskId(7665L);

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);

        // when
        writer.write(buffer, 50);

        // then
        assertThat(writer.getLength()).isEqualTo(MessageHeaderDecoder.ENCODED_LENGTH +
                SingleTaskAckDecoder.BLOCK_LENGTH);

    }
}

