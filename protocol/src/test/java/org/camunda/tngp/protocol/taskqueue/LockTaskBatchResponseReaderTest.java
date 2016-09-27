package org.camunda.tngp.protocol.taskqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Test;

import org.agrona.concurrent.UnsafeBuffer;

public class LockTaskBatchResponseReaderTest
{

    public static final byte[] PAYLOAD1 = "does".getBytes(StandardCharsets.UTF_8);
    public static final byte[] PAYLOAD2 = "not".getBytes(StandardCharsets.UTF_8);
    public static final byte[] PAYLOAD3 = "compute".getBytes(StandardCharsets.UTF_8);

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

        bodyEncoder.wrap(messageBuffer, OFFSET + headerEncoder.encodedLength())
          .consumerId(87)
          .lockTime(3456789L)
          .tasksCount(3)
          .next()
              .taskId(6)
              .wfInstanceId(106)
              .putPayload(PAYLOAD1, 0, PAYLOAD1.length)
          .next()
              .taskId(7)
              .wfInstanceId(107)
              .putPayload(PAYLOAD2, 0, PAYLOAD2.length)
          .next()
              .taskId(8)
              .wfInstanceId(108)
              .putPayload(PAYLOAD3, 0, PAYLOAD3.length);

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
        assertThat(reader.lockTime()).isEqualTo(3456789L);

        reader.nextTask();
        assertThat(reader.currentTaskId()).isEqualTo(6);
        assertThat(reader.currentTaskWfInstanceId()).isEqualTo(106);
        assertThatBuffer(reader.currentTaskPayload()).hasCapacity(PAYLOAD1.length);
        assertThatBuffer(reader.currentTaskPayload()).hasBytes(PAYLOAD1);

        reader.nextTask();
        assertThat(reader.currentTaskId()).isEqualTo(7);
        assertThat(reader.currentTaskWfInstanceId()).isEqualTo(107);
        assertThatBuffer(reader.currentTaskPayload()).hasCapacity(PAYLOAD2.length);
        assertThatBuffer(reader.currentTaskPayload()).hasBytes(PAYLOAD2);

        reader.nextTask();
        assertThat(reader.currentTaskId()).isEqualTo(8);
        assertThat(reader.currentTaskWfInstanceId()).isEqualTo(108);
        assertThatBuffer(reader.currentTaskPayload()).hasCapacity(PAYLOAD3.length);
        assertThatBuffer(reader.currentTaskPayload()).hasBytes(PAYLOAD3);
    }

    @Test
    public void shouldReadSingleTaskMultipleTimes()
    {
        // given
        final LockTaskBatchResponseReader reader = new LockTaskBatchResponseReader();
        reader.wrap(messageBuffer, OFFSET, messageLength);
        reader.nextTask();
        reader.currentTaskId();
        reader.currentTaskPayload();

        // then
        assertThat(reader.currentTaskId()).isEqualTo(6);
        assertThatBuffer(reader.currentTaskPayload()).hasCapacity(PAYLOAD1.length);
        assertThatBuffer(reader.currentTaskPayload()).hasBytes(PAYLOAD1);
    }

    @Test
    public void shouldReadStaticFieldsAfterReadingTask()
    {
        // given
        final LockTaskBatchResponseReader reader = new LockTaskBatchResponseReader();
        reader.wrap(messageBuffer, OFFSET, messageLength);
        reader.nextTask();
        reader.currentTaskId();
        reader.currentTaskPayload();

        // then
        assertThat(reader.consumerId()).isEqualTo(87);
        assertThat(reader.lockTime()).isEqualTo(3456789L);
    }
}
