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

    @Before
    public void writeTasks()
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final LockedTaskBatchEncoder bodyEncoder = new LockedTaskBatchEncoder();

        headerEncoder.wrap(messageBuffer, 0)
            .resourceId(1)
            .schemaId(LockedTaskBatchEncoder.SCHEMA_ID)
            .shardId(2)
            .templateId(LockedTaskBatchEncoder.TEMPLATE_ID)
            .version(LockedTaskBatchEncoder.SCHEMA_VERSION)
            .blockLength(bodyEncoder.sbeBlockLength());

        bodyEncoder.wrap(messageBuffer, headerEncoder.encodedLength())
          .consumerId(87)
          .lockTime(3456789L)
          .tasksCount(3)
          .next()
              .taskId(6)
              .putPayload(PAYLOAD1, 0, PAYLOAD1.length)
          .next()
              .taskId(7)
              .putPayload(PAYLOAD2, 0, PAYLOAD2.length)
          .next()
              .taskId(8)
              .putPayload(PAYLOAD3, 0, PAYLOAD3.length);

        messageLength = bodyEncoder.limit();
    }

    @Test
    public void shouldReadTasks()
    {
        // given
        final LockTaskBatchResponseReader reader = new LockTaskBatchResponseReader();

        // when
        reader.wrap(messageBuffer, 0, messageLength);

        // then
        assertThat(reader.consumerId()).isEqualTo(87);
        assertThat(reader.lockTime()).isEqualTo(3456789L);

        reader.nextTask();
        assertThat(reader.currentTaskId()).isEqualTo(6);
        assertThatBuffer(reader.currentTaskPayload()).hasCapacity(PAYLOAD1.length);
        assertThatBuffer(reader.currentTaskPayload()).hasBytes(PAYLOAD1);

        reader.nextTask();
        assertThat(reader.currentTaskId()).isEqualTo(7);
        assertThatBuffer(reader.currentTaskPayload()).hasCapacity(PAYLOAD2.length);
        assertThatBuffer(reader.currentTaskPayload()).hasBytes(PAYLOAD2);

        reader.nextTask();
        assertThat(reader.currentTaskId()).isEqualTo(8);
        assertThatBuffer(reader.currentTaskPayload()).hasCapacity(PAYLOAD3.length);
        assertThatBuffer(reader.currentTaskPayload()).hasBytes(PAYLOAD3);
    }

    @Test
    public void shouldReadSingleTaskMultipleTimes()
    {
        // given
        final LockTaskBatchResponseReader reader = new LockTaskBatchResponseReader();
        reader.wrap(messageBuffer, 0, messageLength);
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
        reader.wrap(messageBuffer, 0, messageLength);
        reader.nextTask();
        reader.currentTaskId();
        reader.currentTaskPayload();

        // then
        assertThat(reader.consumerId()).isEqualTo(87);
        assertThat(reader.lockTime()).isEqualTo(3456789L);
    }
}
