package org.camunda.tngp.broker.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.taskqueue.handler.TaskTypeHash;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TaskInstanceWriterTest
{
    protected static final byte[] TASK_TYPE = "baz".getBytes(StandardCharsets.UTF_8);

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected TaskInstanceDecoder bodyDecoder = new TaskInstanceDecoder();

    @Test
    public void shouldWriteTaskInstance()
    {
        // given
        final TaskInstanceWriter writer = new TaskInstanceWriter();
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);

        // when
        writer
            .id(7L)
            .taskType(new UnsafeBuffer(TASK_TYPE), 0, TASK_TYPE.length)
            .wfActivityInstanceEventKey(9L)
            .wfRuntimeResourceId(123)
            .write(buffer, 67);

        // then
        headerDecoder.wrap(buffer, 67);
        assertThat(headerDecoder.blockLength()).isEqualTo(TaskInstanceDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.encodedLength()).isEqualTo(MessageHeaderDecoder.ENCODED_LENGTH);
        assertThat(headerDecoder.resourceId()).isEqualTo(0);
        assertThat(headerDecoder.schemaId()).isEqualTo(TaskInstanceDecoder.SCHEMA_ID);
        assertThat(headerDecoder.version()).isEqualTo(TaskInstanceDecoder.SCHEMA_VERSION);
        assertThat(headerDecoder.templateId()).isEqualTo(TaskInstanceDecoder.TEMPLATE_ID);

        bodyDecoder.wrap(buffer, 67 + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(bodyDecoder.encodedLength()).isEqualTo(TaskInstanceDecoder.BLOCK_LENGTH);
        assertThat(bodyDecoder.id()).isEqualTo(7L);

        // workaround for https://github.com/camunda-tngp/tasks/issues/16
        final long unsignedLockOwnerId = Integer.toUnsignedLong((int) bodyDecoder.lockOwnerId());
        assertThat(unsignedLockOwnerId).isEqualTo(TaskInstanceDecoder.lockOwnerIdNullValue());
        assertThat(bodyDecoder.lockTime()).isEqualTo(TaskInstanceDecoder.lockTimeNullValue());
        assertThat(bodyDecoder.prevVersionPosition()).isEqualTo(TaskInstanceDecoder.prevVersionPositionNullValue());
        assertThat(bodyDecoder.state()).isEqualTo(TaskInstanceState.NEW);
        assertThat(bodyDecoder.taskTypeHash()).isEqualTo(TaskTypeHash.hashCode(TASK_TYPE, TASK_TYPE.length));

        final UnsafeBuffer decodedTaskType = new UnsafeBuffer(new byte[TASK_TYPE.length + 3]);
        bodyDecoder.getTaskType(decodedTaskType, 3, TASK_TYPE.length);
        assertThatBuffer(decodedTaskType).hasBytes(TASK_TYPE, 3);
    }

    @Test
    public void shouldProvideEncodedLength()
    {
        // given
        final TaskInstanceWriter writer = new TaskInstanceWriter();
        writer
            .id(7L)
            .taskType(new UnsafeBuffer(TASK_TYPE), 0, TASK_TYPE.length)
            .wfActivityInstanceEventKey(9L)
            .wfRuntimeResourceId(123);

        // when
        final int length = writer.getLength();

        // then
        assertThat(length).isEqualTo(MessageHeaderEncoder.ENCODED_LENGTH +
                TaskInstanceDecoder.BLOCK_LENGTH +
                TaskInstanceDecoder.payloadHeaderLength() +
                TaskInstanceDecoder.taskTypeHeaderLength() +
                TASK_TYPE.length);

    }
}
