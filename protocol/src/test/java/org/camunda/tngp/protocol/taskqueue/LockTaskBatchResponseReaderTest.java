package org.camunda.tngp.protocol.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder.TasksEncoder;
import org.junit.Before;
import org.junit.Test;

public class LockTaskBatchResponseReaderTest
{

    protected UnsafeBuffer messageBuffer = new UnsafeBuffer(new byte[1024 * 1024]);
    protected int messageLength;

    public static final int OFFSET = 20;

    @Before
    public void writeTasks()
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final LockedTaskBatchEncoder bodyEncoder = new LockedTaskBatchEncoder();

        headerEncoder.wrap(messageBuffer, OFFSET)
            .resourceId(1)
            .schemaId(LockedTaskBatchEncoder.SCHEMA_ID)
            .shardId(2)
            .templateId(LockedTaskBatchEncoder.TEMPLATE_ID)
            .version(LockedTaskBatchEncoder.SCHEMA_VERSION)
            .blockLength(bodyEncoder.sbeBlockLength());

        final TasksEncoder tasksEncoder = bodyEncoder.wrap(messageBuffer, OFFSET + headerEncoder.encodedLength())
            .consumerId(87)
            .tasksCount(3);

        tasksEncoder.next()
            .task()
              .id(6)
              .lockTime(123)
              .wfInstanceId(106);
        tasksEncoder.next()
            .task()
              .id(7)
              .lockTime(234)
              .wfInstanceId(107);
        tasksEncoder.next()
            .task()
              .id(8)
              .lockTime(345)
              .wfInstanceId(108);

        messageLength = bodyEncoder.limit() - OFFSET;
    }

    @Test
    public void shouldReadTasks()
    {
        // given
        final LockTaskBatchResponseReader reader = new LockTaskBatchResponseReader();

        // when
        reader.wrap(messageBuffer, OFFSET, messageLength);

        // then
        assertThat(reader.consumerId()).isEqualTo(87);

        reader.nextTask();
        assertThat(reader.currentTaskId()).isEqualTo(6);
        assertThat(reader.currentTaskWfInstanceId()).isEqualTo(106);
        assertThat(reader.currentTaskLockTime()).isEqualTo(123);

        reader.nextTask();
        assertThat(reader.currentTaskId()).isEqualTo(7);
        assertThat(reader.currentTaskWfInstanceId()).isEqualTo(107);
        assertThat(reader.currentTaskLockTime()).isEqualTo(234);

        reader.nextTask();
        assertThat(reader.currentTaskId()).isEqualTo(8);
        assertThat(reader.currentTaskWfInstanceId()).isEqualTo(108);
        assertThat(reader.currentTaskLockTime()).isEqualTo(345);
    }

    @Test
    public void shouldReadSingleTaskMultipleTimes()
    {
        // given
        final LockTaskBatchResponseReader reader = new LockTaskBatchResponseReader();
        reader.wrap(messageBuffer, OFFSET, messageLength);
        reader.nextTask();
        reader.currentTaskId();
        reader.currentTaskLockTime();

        // then
        assertThat(reader.currentTaskId()).isEqualTo(6);
    }

    @Test
    public void shouldReadStaticFieldsAfterReadingTask()
    {
        // given
        final LockTaskBatchResponseReader reader = new LockTaskBatchResponseReader();
        reader.wrap(messageBuffer, OFFSET, messageLength);
        reader.nextTask();
        reader.currentTaskId();
        reader.currentTaskLockTime();

        // then
        assertThat(reader.consumerId()).isEqualTo(87);
    }
}
