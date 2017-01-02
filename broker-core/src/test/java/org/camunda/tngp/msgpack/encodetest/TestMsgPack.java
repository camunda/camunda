package org.camunda.tngp.msgpack.encodetest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.junit.Test;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

public class TestMsgPack
{

    @Test
    public void doIt() throws IOException
    {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final MessagePacker payloadPacker = MessagePack.newDefaultPacker(baos);

        payloadPacker.packMapHeader(4)
            .packString("key1")
            .packString("aValue")
            .packString("key2")
            .packString("alsoaValue")
            .packString("key3")
            .packString("anotherValue")
            .packString("key4")
            .packString("yetAnotherValue");

        payloadPacker.flush();

        final byte[] payload = baos.toByteArray();
        baos.reset();

        final MessagePacker eventPacker = MessagePack.newDefaultPacker(baos);

        eventPacker.packMapHeader(5)
            .packString("event")
            .packString("CREATE")
            .packString("lockTime")
            .packLong(System.currentTimeMillis())
            .packString("type")
            .packString("someTaskType")
            .packString("payload")
                .packBinaryHeader(payload.length)
                .writePayload(payload)
            .packString("headers")
                .packMapHeader(2)
                .packString("key1")
                .packString("value")
                .packString("key2")
                .packString("value");

        eventPacker.flush();

        final byte[] byteArray = baos.toByteArray();

        final MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(byteArray);

        System.out.println(unpacker.unpackValue());

        final UnsafeBuffer readBuffer = new UnsafeBuffer(byteArray);
        final UnsafeBuffer writeBuffer = new UnsafeBuffer(new byte[readBuffer.capacity()]);

        final TaskEvent taskEvent = new TaskEvent();

        final int iterations = 25_000_000;
        final long before = System.nanoTime();

        long bytesWritten = 0;
        long bytesRead = 0;

        for (int i = 0; i < iterations; i++)
        {
            taskEvent.reset();
            bytesRead += readBuffer.capacity();
            taskEvent.wrap(readBuffer, 0, readBuffer.capacity());
            bytesWritten += writeBuffer.capacity();
            taskEvent.write(writeBuffer, 0);
        }

        System.out.println(taskEvent);

        final long after = System.nanoTime();
        final long runtime = after - before;

        System.out.format("Iterations: %d\n", iterations);
        System.out.format("Took: %gs\n", 0.001  * TimeUnit.NANOSECONDS.toMillis(runtime));
        System.out.format("Throughput (avg, ops/us): %g\n", (((double)iterations) / TimeUnit.NANOSECONDS.toMicros(runtime)));
        System.out.format("Throughput (avg, ops/s): %g\n", ((double)iterations) / TimeUnit.NANOSECONDS.toSeconds(runtime));
        System.out.format("Read Throughput (avg, MB/s): %g\n", ((double)bytesRead) / TimeUnit.NANOSECONDS.toSeconds(runtime) / 1000 / 1000);
        System.out.format("Write Throughput (avg, MB/s): %g\n", ((double)bytesWritten) / TimeUnit.NANOSECONDS.toSeconds(runtime) / 1000 / 1000);
    }

}
