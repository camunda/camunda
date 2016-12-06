package org.camunda.tngp.broker;

import java.util.concurrent.TimeUnit;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.taskqueue.processor.TaskEvent;
import org.camunda.tngp.broker.taskqueue.processor.stuff.EncodedStuff;
import org.camunda.tngp.protocol.clientapi.TaskEventType;
import org.junit.Test;

public class DecodeTest
{
    @Test
    public void encodeTest()
    {
        final TaskEvent taskEvent = new TaskEvent();

        final DirectBuffer taskType = new UnsafeBuffer(new byte[24]);
        final DirectBuffer payload = new UnsafeBuffer(new byte[1024 * 24]);

        taskEvent.setEvtType(TaskEventType.CREATE);
        taskEvent.setLockTime(1000);
        taskEvent.getPayload().get(taskType.capacity(), (buffer, o, l) -> taskType.getBytes(0, buffer, o, l));
        taskEvent.getTaskType().get(payload.capacity(), (buffer, o, l) -> payload.getBytes(0, buffer, o, l));

        final EncodedStuff encodedStuff = new EncodedStuff();

        encodedStuff.encode(taskEvent);

        final DirectBuffer encodedBuffer = encodedStuff.getBuffer();

        final long before = System.nanoTime();

        for (int i = 0; i < 10_000_000; i++)
        {
            taskEvent.decode(encodedBuffer, 0);
            taskEvent.reset();
        }

        final long after = System.nanoTime();

        System.out.println(TimeUnit.NANOSECONDS.toMillis(after - before));

    }

}
