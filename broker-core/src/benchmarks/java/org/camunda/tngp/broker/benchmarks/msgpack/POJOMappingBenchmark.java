package org.camunda.tngp.broker.benchmarks.msgpack;

import java.util.concurrent.TimeUnit;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
public class POJOMappingBenchmark
{

    @Benchmark
    @Threads(1)
    public void performMappingCycle(POJOMappingContext ctx) throws Exception
    {
        final TaskEvent taskEvent = ctx.getTaskEvent();
        final DirectBuffer encodedTaskEvent = ctx.getEncodedTaskEvent();
        final MutableDirectBuffer writeBuffer = ctx.getWriteBuffer();

        taskEvent.reset();
        taskEvent.wrap(encodedTaskEvent, 0, encodedTaskEvent.capacity());
        taskEvent.write(writeBuffer, 0);
    }


}
