package org.camunda.tngp.broker.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TaskInstanceReaderTest
{

    protected static final byte[] TASK_TYPE = "fooo".getBytes(StandardCharsets.UTF_8);
    protected static final byte[] PAYLOAD = "barr".getBytes(StandardCharsets.UTF_8);

    protected UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);

    protected int encodeTaskInstance(byte[] payload, UnsafeBuffer buffer)
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final TaskInstanceEncoder bodyEncoder = new TaskInstanceEncoder();

        headerEncoder.wrap(buffer, 0)
            .schemaId(bodyEncoder.sbeSchemaId())
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion())
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(5)
            .shardId(7);

        bodyEncoder.wrap(buffer, headerEncoder.encodedLength())
            .id(876L)
            .lockOwnerId(456789L)
            .lockTime(97543L)
            .prevVersionPosition(4896L)
            .state(TaskInstanceState.LOCKED)
            .taskTypeHash(56789L)
            .version(865)
            .wfActivityInstanceEventKey(90L)
            .wfRuntimeResourceId(5789)
            .putTaskType(TASK_TYPE, 0, TASK_TYPE.length)
            .putPayload(payload, 0, payload.length);

        return headerEncoder.encodedLength() + bodyEncoder.encodedLength();
    }

    @Test
    public void shouldReadTaskInstance()
    {
        // given
        final int writtenLength = encodeTaskInstance(PAYLOAD, buffer);
        final TaskInstanceReader reader = new TaskInstanceReader();

        // when
        reader.wrap(buffer, 0, writtenLength);

        // then
        assertThat(reader.id()).isEqualTo(876L);
        assertThat(reader.lockOwnerId()).isEqualTo(456789L);
        assertThat(reader.lockTime()).isEqualTo(97543L);
        assertThat(reader.prevVersionPosition()).isEqualTo(4896L);
        assertThat(reader.resourceId()).isEqualTo(5);
        assertThat(reader.shardId()).isEqualTo(7);
        assertThat(reader.state()).isEqualTo(TaskInstanceState.LOCKED);
        assertThat(reader.taskTypeHash()).isEqualTo(56789L);
        assertThat(reader.version()).isEqualTo(865);
        assertThat(reader.wfActivityInstanceEventKey()).isEqualTo(90L);
        assertThat(reader.wfRuntimeResourceId()).isEqualTo(5789);

        assertThatBuffer(reader.getPayload()).hasCapacity(PAYLOAD.length);
        assertThatBuffer(reader.getPayload()).hasBytes(PAYLOAD);
        assertThatBuffer(reader.getTaskType()).hasCapacity(TASK_TYPE.length);
        assertThatBuffer(reader.getTaskType()).hasBytes(TASK_TYPE);
    }

    @Test
    public void shouldReadTaskInstanceWithEmptyPayload()
    {
        // given
        final int expectedCapacity = MessageHeaderEncoder.ENCODED_LENGTH +
                TaskInstanceEncoder.BLOCK_LENGTH +
                TaskInstanceEncoder.taskTypeHeaderLength() +
                TASK_TYPE.length +
                TaskInstanceEncoder.payloadHeaderLength();
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[expectedCapacity]);
        encodeTaskInstance(new byte[0], buffer);
        final TaskInstanceReader reader = new TaskInstanceReader();

        // when
        reader.wrap(buffer, 0, expectedCapacity);

        // then
        assertThatBuffer(reader.getPayload()).hasCapacity(0);
    }

}
