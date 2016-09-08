package org.camunda.tngp.benchmark.dispatcher;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

@State(Scope.Thread)
public class DispatcherBenchmarkThreadState
{

    static AtomicInteger threadIdGenerator = new AtomicInteger(0);

    MutableDirectBuffer msg;

    int threadId;

    @Setup
    public void setup()
    {
        threadId = threadIdGenerator.incrementAndGet() % 3;
        msg = new UnsafeBuffer(ByteBuffer.allocateDirect(BitUtil.SIZE_OF_INT));
    }

}
