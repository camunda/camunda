package org.camunda.tngp.broker.wf.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.protocol.wf.runtime.MessageHeaderDecoder;
import org.camunda.tngp.protocol.wf.runtime.MessageHeaderEncoder;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceResponseDecoder;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceResponseEncoder;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class StartProcessInstanceResponseWriterTest
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
        StartProcessInstanceResponseWriter writer = new StartProcessInstanceResponseWriter();

        // when
        writer
            .processInstanceId(45678L)
            .write(eventBuffer, 12);

        // then
        MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        StartWorkflowInstanceResponseDecoder bodyDecoder = new StartWorkflowInstanceResponseDecoder();

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
        assertThat(bodyDecoder.wfInstanceId()).isEqualTo(45678L);
    }

    @Test
    public void shouldEstimateWriteLength()
    {
        // given
        StartProcessInstanceResponseWriter writer = new StartProcessInstanceResponseWriter();

        writer
            .processInstanceId(45678L)
            .write(eventBuffer, 0);

        // when
        int estimatedLength = writer.getLength();

        // then
        assertThat(estimatedLength).isEqualTo(MessageHeaderEncoder.ENCODED_LENGTH + StartWorkflowInstanceResponseEncoder.BLOCK_LENGTH);
    }
}
