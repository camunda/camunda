package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.client.impl.cmd.wf.start.StartWorkflowInstanceResponseReader;
import org.camunda.tngp.protocol.wf.runtime.MessageHeaderEncoder;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceResponseEncoder;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class StartWorkflowInstanceResponseReaderTest
{

    protected UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
    protected int encodedLength;

    @Before
    public void writeToBuffer()
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final StartWorkflowInstanceResponseEncoder bodyEncoder = new StartWorkflowInstanceResponseEncoder();

        headerEncoder
            .wrap(buffer, 0)
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(123)
            .schemaId(bodyEncoder.sbeSchemaId())
            .shardId(456)
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder
            .wrap(buffer, headerEncoder.encodedLength())
            .wfInstanceId(54L);

        encodedLength = headerEncoder.encodedLength() + bodyEncoder.encodedLength();
    }

    @Test
    public void shouldReadInstanceId()
    {
        // given
        final StartWorkflowInstanceResponseReader reader = new StartWorkflowInstanceResponseReader();

        // when
        reader.wrap(buffer, 0, encodedLength);

        // then
        assertThat(reader.wfInstanceId()).isEqualTo(54L);
    }
}
