package org.camunda.tngp.broker.wf.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.test.util.BufferAssert;
import org.camunda.tngp.protocol.wf.runtime.MessageHeaderEncoder;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceEncoder;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class StartProcessInstanceRequestReaderTest
{
    protected UnsafeBuffer eventBuffer = new UnsafeBuffer(new byte[1024 * 1024]);
    protected int eventLength;

    @Before
    public void writeEventToBuffer()
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
            .wfTypeId(123L)
            .wfTypeKey("foo");

        eventLength = headerEncoder.encodedLength() + bodyEncoder.encodedLength();
    }

    @Test
    public void shouldReadRequest()
    {
        // given
        final StartProcessInstanceRequestReader reader = new StartProcessInstanceRequestReader();

        // when
        reader.wrap(eventBuffer, 0, eventLength);

        // then
        assertThat(reader.wfTypeId()).isEqualTo(123L);
        BufferAssert.assertThat(reader.wfTypeKey()).hasBytes("foo".getBytes(StandardCharsets.UTF_8));
    }

}
