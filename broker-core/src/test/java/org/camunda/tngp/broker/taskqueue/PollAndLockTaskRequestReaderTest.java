package org.camunda.tngp.broker.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.protocol.taskqueue.PollAndLockTasksEncoder;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class PollAndLockTaskRequestReaderTest
{

    protected UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);
    protected int writtenLength;

    protected static final byte[] TASK_TYPE = "camunda".getBytes(StandardCharsets.UTF_8);

    @Before
    public void writeToBuffer()
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final PollAndLockTasksEncoder bodyEncoder = new PollAndLockTasksEncoder();

        headerEncoder.wrap(buffer, 56)
            .resourceId(1)
            .schemaId(bodyEncoder.sbeSchemaId())
            .shardId(2)
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion())
            .blockLength(bodyEncoder.sbeBlockLength());

        bodyEncoder.wrap(buffer, 56 + headerEncoder.encodedLength())
            .consumerId(5)
            .lockTime(89754L)
            .maxTasks(6)
            .putTaskType(TASK_TYPE, 0, TASK_TYPE.length);

        writtenLength = headerEncoder.encodedLength() + bodyEncoder.encodedLength();
    }

    @Test
    public void shouldReaderFromBuffer()
    {
        // given
        final PollAndLockTaskRequestReader reader = new PollAndLockTaskRequestReader();

        // when
        reader.wrap(buffer, 56, writtenLength);

        // then
        assertThat(reader.consumerId()).isEqualTo(5);
        assertThat(reader.maxTasks()).isEqualTo(6);
        assertThat(reader.lockTime()).isEqualTo(89754L);
        assertThatBuffer(reader.taskType()).hasCapacity(TASK_TYPE.length);
        assertThatBuffer(reader.taskType()).hasBytes(TASK_TYPE);
    }
}
