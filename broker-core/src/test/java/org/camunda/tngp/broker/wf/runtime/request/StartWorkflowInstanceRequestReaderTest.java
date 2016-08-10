package org.camunda.tngp.broker.wf.runtime.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.wf.runtime.StartWorkflowInstanceRequestReader;
import org.camunda.tngp.protocol.wf.runtime.MessageHeaderEncoder;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceEncoder;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class StartWorkflowInstanceRequestReaderTest
{
    protected UnsafeBuffer eventBuffer = new UnsafeBuffer(new byte[1024 * 1024]);
    protected int eventLength;

    public void writeEventToBuffer(String wfDefinitionKey)
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final StartWorkflowInstanceEncoder bodyEncoder = new StartWorkflowInstanceEncoder();

        headerEncoder.wrap(eventBuffer, 0)
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(1)
            .schemaId(bodyEncoder.sbeSchemaId())
            .shardId(2)
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(eventBuffer, headerEncoder.encodedLength())
            .wfDefinitionId(123L)
            .wfDefinitionKey(wfDefinitionKey);

        eventLength = headerEncoder.encodedLength() + bodyEncoder.encodedLength();
    }

    @Test
    public void shouldReadRequest()
    {
        // given
        writeEventToBuffer("foo");
        final StartWorkflowInstanceRequestReader reader = new StartWorkflowInstanceRequestReader();

        // when
        reader.wrap(eventBuffer, 0, eventLength);

        // then
        assertThat(reader.wfDefinitionId()).isEqualTo(123L);
        assertThatBuffer(reader.wfDefinitionKey()).hasBytes("foo".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void shouldReadRequestWithEmptyDefinitionKey()
    {
        // given
        writeEventToBuffer("");
        final StartWorkflowInstanceRequestReader reader = new StartWorkflowInstanceRequestReader();
        final UnsafeBuffer noPaddingBuffer = new UnsafeBuffer(eventBuffer, 0, eventLength);

        // when
        reader.wrap(noPaddingBuffer, 0, eventLength);

        // then
        assertThatBuffer(reader.wfDefinitionKey()).hasCapacity(0);
    }

}
