package org.camunda.tngp.broker.wf.runtime.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.broker.wf.runtime.StartWorkflowInstanceResponseWriter;
import org.camunda.tngp.protocol.wf.MessageHeaderDecoder;
import org.camunda.tngp.protocol.wf.MessageHeaderEncoder;
import org.camunda.tngp.protocol.wf.StartWorkflowInstanceResponseDecoder;
import org.camunda.tngp.protocol.wf.StartWorkflowInstanceResponseEncoder;
import org.junit.Before;
import org.junit.Test;

import org.agrona.concurrent.UnsafeBuffer;

public class StartWorkflowInstanceResponseWriterTest
{

    protected UnsafeBuffer eventBuffer;

    @Before
    public void setUpBuffer()
    {
        eventBuffer = new UnsafeBuffer(new byte[1024 * 1024]);
    }

    @Test
    public void shouldWriteResponse()
    {
        // given
        final StartWorkflowInstanceResponseWriter writer = new StartWorkflowInstanceResponseWriter();

        // when
        writer
            .id(45678L)
            .get(eventBuffer, 12);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final StartWorkflowInstanceResponseDecoder bodyDecoder = new StartWorkflowInstanceResponseDecoder();

        headerDecoder.wrap(eventBuffer, 12);
        assertThat(headerDecoder.blockLength()).isEqualTo(StartWorkflowInstanceResponseDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.resourceId()).isEqualTo(0);
        assertThat(headerDecoder.schemaId()).isEqualTo(StartWorkflowInstanceResponseDecoder.SCHEMA_ID);
        assertThat(headerDecoder.shardId()).isEqualTo(0);
        assertThat(headerDecoder.templateId()).isEqualTo(StartWorkflowInstanceResponseDecoder.TEMPLATE_ID);

        bodyDecoder.wrap(eventBuffer,
                12 + MessageHeaderDecoder.ENCODED_LENGTH,
                StartWorkflowInstanceResponseDecoder.BLOCK_LENGTH,
                StartWorkflowInstanceResponseDecoder.SCHEMA_VERSION);
        assertThat(bodyDecoder.id()).isEqualTo(45678L);
    }

    @Test
    public void shouldEstimateWriteLength()
    {
        // given
        final StartWorkflowInstanceResponseWriter writer = new StartWorkflowInstanceResponseWriter();

        writer
            .id(45678L)
            .get(eventBuffer, 0);

        // when
        final int estimatedLength = writer.getEncodedLength();

        // then
        assertThat(estimatedLength).isEqualTo(MessageHeaderEncoder.ENCODED_LENGTH + StartWorkflowInstanceResponseEncoder.BLOCK_LENGTH);
    }
}
