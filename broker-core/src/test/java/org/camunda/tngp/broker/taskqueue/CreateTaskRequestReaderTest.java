package org.camunda.tngp.broker.taskqueue;

import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.protocol.taskqueue.CreateTaskInstanceEncoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class CreateTaskRequestReaderTest
{
    protected UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);
    protected int writtenLength;

    protected static final byte[] PAYLOAD = "ayayay".getBytes(StandardCharsets.UTF_8);
    protected static final byte[] TASK_TYPE = "me gusta".getBytes(StandardCharsets.UTF_8);

    @Before
    public void writeToBuffer()
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final CreateTaskInstanceEncoder bodyEncoder = new CreateTaskInstanceEncoder();

        headerEncoder.wrap(buffer, 76)
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(123)
            .schemaId(bodyEncoder.sbeSchemaId())
            .shardId(987)
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(buffer, 76 + headerEncoder.encodedLength())
            .putTaskType(TASK_TYPE, 0, TASK_TYPE.length)
            .putPayload(PAYLOAD, 0, PAYLOAD.length);

        writtenLength = headerEncoder.encodedLength() + bodyEncoder.encodedLength();
    }

    @Test
    public void shouldReadFromBuffer()
    {
        // given
        final CreateTaskInstanceRequestReader reader = new CreateTaskInstanceRequestReader();

        // when
        reader.wrap(buffer, 76, writtenLength);

        // then
        assertThatBuffer(reader.getTaskType()).hasBytes(TASK_TYPE);
        assertThatBuffer(reader.getPayload()).hasBytes(PAYLOAD);
    }
}
