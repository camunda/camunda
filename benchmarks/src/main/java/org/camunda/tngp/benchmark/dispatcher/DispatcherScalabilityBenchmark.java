package org.camunda.tngp.benchmark.dispatcher;

import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Threads;

public class DispatcherScalabilityBenchmark
{
    private static final int BURST_SIZE = Integer.getInteger("burst.size", 1);

    @Benchmark
    @Threads(1)
    public void publishMessage1(DispatcherSupplier dispatcherSupplier, DispatcherBenchmarkThreadState msgBufferSupplier)
    {
        sendBurst(dispatcherSupplier, msgBufferSupplier);
    }

    @Benchmark
    @Threads(2)
    public void publishMessage2(DispatcherSupplier dispatcherSupplier, DispatcherBenchmarkThreadState msgBufferSupplier)
    {
        sendBurst(dispatcherSupplier, msgBufferSupplier);
    }

    @Benchmark
    @Threads(3)
    public void publishMessage3(DispatcherSupplier dispatcherSupplier, DispatcherBenchmarkThreadState msgBufferSupplier)
    {
        sendBurst(dispatcherSupplier, msgBufferSupplier);
    }

    private static void sendBurst(final DispatcherSupplier dispatcherSupplier, final DispatcherBenchmarkThreadState threadState)
    {
        final AtomicBoolean burstCompleteField = dispatcherSupplier.burstCompleteFields[threadState.threadId];
        burstCompleteField.set(false);

        final Dispatcher dispatcher = dispatcherSupplier.dispatcher;
        final MutableDirectBuffer msgBuffer = threadState.msg;
        final int threadId = threadState.threadId;

        for (int i = 0; i < BURST_SIZE; i++)
        {
            msgBuffer.putInt(0, i);

            long offerPos = -1;

            do
            {
                offerPos = dispatcher.offer(msgBuffer, 0, msgBuffer.capacity(), threadId);
            }
            while (offerPos < 0);
        }

        while (!burstCompleteField.get())
        {
            // spin
        }
    }

}