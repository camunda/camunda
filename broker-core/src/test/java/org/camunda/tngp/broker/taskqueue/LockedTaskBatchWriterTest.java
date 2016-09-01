package org.camunda.tngp.broker.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchDecoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchDecoder.TasksDecoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder.TasksEncoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.junit.Test;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class LockedTaskBatchWriterTest
{
    protected UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);

    @Test
    public void shouldWriteWithoutTasks()
    {
        // given
        final LockedTaskBatchWriter writer = new LockedTaskBatchWriter();

        // when
        writer
            .consumerId(1)
            .lockTime(654L)
            .write(buffer, 54);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(buffer, 54);

        final LockedTaskBatchDecoder bodyDecoder = new LockedTaskBatchDecoder();
        bodyDecoder.wrap(buffer, 54 + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(headerDecoder.blockLength()).isEqualTo(bodyDecoder.sbeBlockLength());
        assertThat(headerDecoder.resourceId()).isEqualTo(0);
        assertThat(headerDecoder.schemaId()).isEqualTo(bodyDecoder.sbeSchemaId());
        assertThat(headerDecoder.shardId()).isEqualTo(0);
        assertThat(headerDecoder.templateId()).isEqualTo(bodyDecoder.sbeTemplateId());
        assertThat(headerDecoder.version()).isEqualTo(bodyDecoder.sbeSchemaVersion());

        assertThat(bodyDecoder.consumerId()).isEqualTo(1);
        assertThat(bodyDecoder.lockTime()).isEqualTo(654L);
        assertThat(bodyDecoder.tasks().count()).isEqualTo(0);
    }

    @Test
    public void shouldEstimateWriteLengthWithoutTasks()
    {

        // given
        final LockedTaskBatchWriter writer = new LockedTaskBatchWriter();

        writer
            .consumerId(1)
            .lockTime(654L);

        // when
        final int length = writer.getLength();

        // then
        final int expectedLength =
                MessageHeaderEncoder.ENCODED_LENGTH +
                LockedTaskBatchEncoder.BLOCK_LENGTH +
                TasksEncoder.sbeHeaderSize();

        assertThat(length).isEqualTo(expectedLength);
    }

    @Test
    public void shouldWriteWithMultipleTasks()
    {
        // given
        final LockedTaskBatchWriter writer = new LockedTaskBatchWriter();

        // when
        writer
            .consumerId(1)
            .lockTime(654L)
            .newTasks()
                .appendTask(76, 10076, asBuffer("foo"), 0, 3)
                .appendTask(98, 10098, asBuffer("foobar"), 3, 3)
                .appendTask(123, 10123, asBuffer("foobar"), 1, 2)
            .write(buffer, 54);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(buffer, 54);

        final LockedTaskBatchDecoder bodyDecoder = new LockedTaskBatchDecoder();
        bodyDecoder.wrap(buffer, 54 + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(headerDecoder.blockLength()).isEqualTo(bodyDecoder.sbeBlockLength());
        assertThat(headerDecoder.resourceId()).isEqualTo(0);
        assertThat(headerDecoder.schemaId()).isEqualTo(bodyDecoder.sbeSchemaId());
        assertThat(headerDecoder.shardId()).isEqualTo(0);
        assertThat(headerDecoder.templateId()).isEqualTo(bodyDecoder.sbeTemplateId());
        assertThat(headerDecoder.version()).isEqualTo(bodyDecoder.sbeSchemaVersion());

        assertThat(bodyDecoder.consumerId()).isEqualTo(1);
        assertThat(bodyDecoder.lockTime()).isEqualTo(654L);

        final TasksDecoder tasksDecoder = bodyDecoder.tasks();
        assertThat(tasksDecoder.count()).isEqualTo(3);

        tasksDecoder.next();
        assertThat(tasksDecoder.taskId()).isEqualTo(76);
        assertThat(tasksDecoder.wfInstanceId()).isEqualTo(10076);
        assertThat(tasksDecoder.payload()).isEqualTo("foo");

        tasksDecoder.next();
        assertThat(tasksDecoder.taskId()).isEqualTo(98);
        assertThat(tasksDecoder.payload()).isEqualTo("bar");

        tasksDecoder.next();
        assertThat(tasksDecoder.taskId()).isEqualTo(123);
        assertThat(tasksDecoder.payload()).isEqualTo("oo");
    }

    @Test
    public void shouldEstimateWriteLengthWithMultipleTasks()
    {

        // given
        final LockedTaskBatchWriter writer = new LockedTaskBatchWriter();

        writer
            .consumerId(1)
            .lockTime(654L)
            .newTasks()
                .appendTask(76, 10076, asBuffer("foo"), 0, 3)
                .appendTask(98, 10098, asBuffer("foobar"), 3, 3)
                .appendTask(123, 10123, asBuffer("foobar"), 1, 2);

        // when
        final int length = writer.getLength();

        // then
        int expectedLength =
                MessageHeaderEncoder.ENCODED_LENGTH +
                LockedTaskBatchEncoder.BLOCK_LENGTH +
                TasksEncoder.sbeHeaderSize();

        expectedLength += (TasksEncoder.sbeBlockLength() + TasksEncoder.payloadHeaderLength()) * 3; // static length of tasks
        expectedLength += 3 + 3 + 2; // payload lengths

        assertThat(length).isEqualTo(expectedLength);
    }

    protected static DirectBuffer asBuffer(String content)
    {
        return new UnsafeBuffer(content.getBytes(StandardCharsets.UTF_8));
    }
}
