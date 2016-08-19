package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.client.impl.cmd.taskqueue.PollAndLockRequestWriter;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.protocol.taskqueue.PollAndLockTasksDecoder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.agrona.concurrent.UnsafeBuffer;

public class PollAndLockRequestWriterTest
{
    protected static final byte[] TASK_TYPE = "clintonEmails".getBytes(StandardCharsets.UTF_8);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldWriteRequest()
    {
        // given
        final PollAndLockRequestWriter requestWriter = new PollAndLockRequestWriter();
        requestWriter
            .lockTimeMs(1234L)
            .maxTasks(5)
            .resourceId(1)
            .shardId(2)
            .taskType(TASK_TYPE, 0, TASK_TYPE.length);

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);

        // when
        requestWriter.write(buffer, 13);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(buffer, 13);

        assertThat(headerDecoder.blockLength()).isEqualTo(PollAndLockTasksDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.resourceId()).isEqualTo(1);
        assertThat(headerDecoder.shardId()).isEqualTo(2);
        assertThat(headerDecoder.templateId()).isEqualTo(PollAndLockTasksDecoder.TEMPLATE_ID);
        assertThat(headerDecoder.schemaId()).isEqualTo(PollAndLockTasksDecoder.SCHEMA_ID);
        assertThat(headerDecoder.version()).isEqualTo(PollAndLockTasksDecoder.SCHEMA_VERSION);

        final PollAndLockTasksDecoder bodyDecoder = new PollAndLockTasksDecoder();
        bodyDecoder.wrap(buffer, 13 + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(bodyDecoder.consumerId()).isEqualTo(0);
        assertThat(bodyDecoder.lockTime()).isEqualTo(1234L);
        assertThat(bodyDecoder.maxTasks()).isEqualTo(5);
        assertThat(bodyDecoder.taskType()).isEqualTo("clintonEmails");

    }

    @Test
    public void shouldReturnWriteLength()
    {
        // given
        final PollAndLockRequestWriter requestWriter = new PollAndLockRequestWriter();
        requestWriter
            .lockTimeMs(1234L)
            .maxTasks(5)
            .resourceId(1)
            .shardId(2)
            .taskType(TASK_TYPE, 0, TASK_TYPE.length);

        // when
        final int length = requestWriter.getLength();

        // then
        assertThat(length).isEqualTo(MessageHeaderDecoder.ENCODED_LENGTH +
                PollAndLockTasksDecoder.BLOCK_LENGTH +
                PollAndLockTasksDecoder.taskTypeHeaderLength() +
                TASK_TYPE.length);
    }

    @Test
    public void shouldValidateMissingTaskType()
    {
        // given
        final PollAndLockRequestWriter requestWriter = new PollAndLockRequestWriter();
        requestWriter
            .lockTimeMs(1234L)
            .maxTasks(5)
            .resourceId(1)
            .shardId(2);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("No task type specified");

        // when
        requestWriter.validate();
    }

    @Test
    public void shouldValidateNegativeMaxTasks()
    {
        // given
        final PollAndLockRequestWriter requestWriter = new PollAndLockRequestWriter();
        requestWriter
            .lockTimeMs(1234L)
            .maxTasks(-5)
            .resourceId(1)
            .shardId(2)
            .taskType(TASK_TYPE, 0, TASK_TYPE.length);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("maxTasks must greater than or equal to 0");

        // when
        requestWriter.validate();
    }

    @Test
    public void shouldValidateMissingTaskQueueid()
    {
        // given
        final PollAndLockRequestWriter requestWriter = new PollAndLockRequestWriter();
        requestWriter
            .lockTimeMs(1234L)
            .maxTasks(5)
            .shardId(2)
            .taskType(TASK_TYPE, 0, TASK_TYPE.length);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("No task queue id set");

        // when
        requestWriter.validate();
    }

    @Test
    public void shouldResetAfterWrite()
    {
        // given
        final PollAndLockRequestWriter requestWriter = new PollAndLockRequestWriter();
        requestWriter
            .lockTimeMs(1234L)
            .maxTasks(5)
            .resourceId(1)
            .shardId(2)
            .taskType(TASK_TYPE, 0, TASK_TYPE.length);

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);

        requestWriter.write(buffer, 0);

        // when
        requestWriter.write(buffer, 0);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(buffer, 0);

        assertThat(headerDecoder.shardId()).isEqualTo(MessageHeaderDecoder.shardIdNullValue());
        assertThat(headerDecoder.resourceId()).isEqualTo(MessageHeaderDecoder.resourceIdNullValue());

        final PollAndLockTasksDecoder bodyDecoder = new PollAndLockTasksDecoder();
        bodyDecoder.wrap(buffer, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(bodyDecoder.consumerId()).isEqualTo(0);
        assertThat(bodyDecoder.lockTime()).isEqualTo(PollAndLockTasksDecoder.lockTimeNullValue());
        assertThat(bodyDecoder.maxTasks()).isEqualTo(PollAndLockTasksDecoder.maxTasksNullValue());
        assertThat(bodyDecoder.taskType()).isEqualTo("");
    }

}
