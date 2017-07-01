package io.zeebe.broker.benchmarks.msgpack;

import java.util.concurrent.TimeUnit;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import io.zeebe.broker.taskqueue.data.TaskEvent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
public class POJOMappingBenchmark
{

    @Benchmark
    @Threads(1)
    public void performReadingOptimalOrder(POJOMappingContext ctx)
    {
        final TaskEvent taskEvent = ctx.getTaskEvent();
        final DirectBuffer encodedTaskEvent = ctx.getOptimalOrderEncodedTaskEvent();

        taskEvent.reset();
        taskEvent.wrap(encodedTaskEvent, 0, encodedTaskEvent.capacity());
    }

    @Benchmark
    @Threads(1)
    public void performReadingReverseOrder(POJOMappingContext ctx)
    {
        final TaskEvent taskEvent = ctx.getTaskEvent();
        final DirectBuffer encodedTaskEvent = ctx.getReverseOrderEncodedTaskEvent();

        taskEvent.reset();
        taskEvent.wrap(encodedTaskEvent, 0, encodedTaskEvent.capacity());
    }

    @Benchmark
    @Threads(1)
    public void performMappingCycleOptimalEncodedOrder(POJOMappingContext ctx) throws Exception
    {
        final TaskEvent taskEvent = ctx.getTaskEvent();
        final DirectBuffer encodedTaskEvent = ctx.getOptimalOrderEncodedTaskEvent();
        final MutableDirectBuffer writeBuffer = ctx.getWriteBuffer();

        taskEvent.reset();
        taskEvent.wrap(encodedTaskEvent, 0, encodedTaskEvent.capacity());
        taskEvent.write(writeBuffer, 0);
    }

    @Benchmark
    @Threads(1)
    public void performMappingCycleReverseEncodedOrder(POJOMappingContext ctx) throws Exception
    {
        final TaskEvent taskEvent = ctx.getTaskEvent();
        final DirectBuffer encodedTaskEvent = ctx.getReverseOrderEncodedTaskEvent();
        final MutableDirectBuffer writeBuffer = ctx.getWriteBuffer();

        taskEvent.reset();
        taskEvent.wrap(encodedTaskEvent, 0, encodedTaskEvent.capacity());
        taskEvent.write(writeBuffer, 0);
    }


}
