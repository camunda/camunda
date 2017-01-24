package org.camunda.tngp.client.benchmark.msgpack;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
public class POJOSerializationBenchmark
{

    @Benchmark
    @Threads(1)
    public void serialize(POJOSerializationContext ctx) throws Exception
    {

        final MsgPackSerializer serializer = ctx.getSerializer();
        final POJOFactory pojoFactory = ctx.getPojoFactory();
        serializer.serialize(pojoFactory.build(), ctx.getTargetBuffer(), 0);
    }
}
