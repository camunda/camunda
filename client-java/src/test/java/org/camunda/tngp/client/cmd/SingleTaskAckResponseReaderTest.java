package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.client.impl.cmd.TaskAckResponseHandler;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.protocol.taskqueue.SingleTaskAckEncoder;
import org.junit.Before;
import org.junit.Test;

import org.agrona.concurrent.UnsafeBuffer;

public class SingleTaskAckResponseReaderTest
{

    protected UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
    protected int writtenLength;

    @Before
    public void writeToBuffer()
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final SingleTaskAckEncoder bodyEncoder = new SingleTaskAckEncoder();

        headerEncoder
            .wrap(buffer, 0)
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(5)
            .schemaId(SingleTaskAckEncoder.SCHEMA_ID)
            .shardId(7)
            .templateId(SingleTaskAckEncoder.TEMPLATE_ID)
            .version(SingleTaskAckEncoder.SCHEMA_VERSION);

        bodyEncoder
            .wrap(buffer, headerEncoder.encodedLength())
            .taskId(1234L);

        writtenLength = MessageHeaderEncoder.ENCODED_LENGTH + SingleTaskAckEncoder.BLOCK_LENGTH;
    }

    @Test
    public void shouldReadResponse()
    {
        // given
        final TaskAckResponseHandler responseHandler = new TaskAckResponseHandler();

        // when
        final Long taskId = responseHandler.readResponse(buffer, 0, writtenLength);

        // then
        assertThat(taskId).isEqualTo(1234L);
    }
}
