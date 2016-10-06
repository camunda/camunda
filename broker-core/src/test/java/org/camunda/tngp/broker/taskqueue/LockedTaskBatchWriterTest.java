package org.camunda.tngp.broker.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchDecoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchDecoder.TasksDecoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder.TasksEncoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskDecoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskWriter;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.junit.Test;

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
        assertThat(bodyDecoder.tasks().count()).isEqualTo(0);
    }

    @Test
    public void shouldEstimateWriteLengthWithoutTasks()
    {

        // given
        final LockedTaskBatchWriter writer = new LockedTaskBatchWriter();

        writer
            .consumerId(1);

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
        final LockedTaskWriter taskWriter = new LockedTaskWriter();

        // when
        writer
            .consumerId(1)
            .newTasks()
                .appendTask(taskWriter.id(76).workflowInstanceId(10076).lockTime(123L))
                .appendTask(taskWriter.id(98).workflowInstanceId(10098).lockTime(234L))
                .appendTask(taskWriter.id(123).workflowInstanceId(10123).lockTime(345L))
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

        final TasksDecoder tasksDecoder = bodyDecoder.tasks();
        assertThat(tasksDecoder.count()).isEqualTo(3);

        LockedTaskDecoder taskDecoder = tasksDecoder.next().task();
        assertThat(taskDecoder.id()).isEqualTo(76);
        assertThat(taskDecoder.wfInstanceId()).isEqualTo(10076);
        assertThat(taskDecoder.lockTime()).isEqualTo(123L);

        taskDecoder = tasksDecoder.next().task();
        assertThat(taskDecoder.id()).isEqualTo(98);
        assertThat(taskDecoder.wfInstanceId()).isEqualTo(10098);
        assertThat(taskDecoder.lockTime()).isEqualTo(234L);

        taskDecoder = tasksDecoder.next().task();
        assertThat(taskDecoder.id()).isEqualTo(123);
        assertThat(taskDecoder.wfInstanceId()).isEqualTo(10123);
        assertThat(taskDecoder.lockTime()).isEqualTo(345L);
    }

    @Test
    public void shouldEstimateWriteLengthWithMultipleTasks()
    {

        // given
        final LockedTaskBatchWriter writer = new LockedTaskBatchWriter();
        final LockedTaskWriter taskWriter = new LockedTaskWriter();

        writer
            .consumerId(1)
            .newTasks()
                .appendTask(taskWriter.id(76).workflowInstanceId(10076).lockTime(123L))
                .appendTask(taskWriter.id(98).workflowInstanceId(10089).lockTime(234L))
                .appendTask(taskWriter.id(123).workflowInstanceId(10123).lockTime(345L));

        // when
        final int length = writer.getLength();

        // then
        int expectedLength =
                MessageHeaderEncoder.ENCODED_LENGTH +
                LockedTaskBatchEncoder.BLOCK_LENGTH +
                TasksEncoder.sbeHeaderSize();

        expectedLength += TasksEncoder.sbeBlockLength() * 3; // static length of tasks

        assertThat(length).isEqualTo(expectedLength);
    }

    protected static DirectBuffer asBuffer(String content)
    {
        return new UnsafeBuffer(content.getBytes(StandardCharsets.UTF_8));
    }
}
