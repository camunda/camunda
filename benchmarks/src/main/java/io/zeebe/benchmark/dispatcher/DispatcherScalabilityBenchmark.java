package io.zeebe.benchmark.dispatcher;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.dispatcher.*;
import org.openjdk.jmh.annotations.*;

public class DispatcherScalabilityBenchmark
{
    static final AtomicInteger THREAD_ID_GENERATOR = new AtomicInteger(0);
    private static final int BURST_SIZE = Integer.getInteger("burst.size", 1);

    @State(Scope.Benchmark)
    public static class SharedState implements FragmentHandler
    {
        Dispatcher dispatcher;
        Subscription subscription;
        Thread consumer;
        volatile boolean exit = false;
        AtomicBoolean[] burstCompleteFields;
        int[] messages;

        @Setup
        public void createDispatcher()
        {
            burstCompleteFields = new AtomicBoolean[3];
            for (int i = 0; i < burstCompleteFields.length; i++)
            {
                burstCompleteFields[i] = new AtomicBoolean(false);
            }

            messages = new int[BURST_SIZE];
            for (int i = 0; i < BURST_SIZE; i++)
            {
                messages[i] = -(BURST_SIZE - i);
            }

            dispatcher = Dispatchers.create("default")
                    .bufferSize(1024 * 1024 * 32)
                    .build();

            subscription = dispatcher.openSubscription("test");

            consumer = new Thread(() ->
            {
                while (!exit)
                {
                    subscription.poll(this, BURST_SIZE);
                }
            });

            consumer.start();
        }

        public int onFragment(DirectBuffer msg, int offset, int length, int streamId, boolean foo)
        {
            final int messageId = msg.getInt(offset);

            if (messageId >= 0)
            {
                burstCompleteFields[messageId].set(true);
            }

            return CONSUME_FRAGMENT_RESULT;
        }

        @TearDown
        public void stop()
        {
            exit = true;

            try
            {
                consumer.join();
                dispatcher.close();
            }
            catch (InterruptedException e)
            {
                LangUtil.rethrowUnchecked(e);
            }

        }
    }

    @State(Scope.Thread)
    public static class ThreadState
    {
        protected int threadId;
        protected Dispatcher dispatcher;
        protected AtomicBoolean burstCompleteField;
        protected int[] messages;
        protected ClaimedFragment claimedFragment;
        protected UnsafeBuffer sendBuffer;

        @Setup
        public void setup(final SharedState sharedState)
        {
            threadId = THREAD_ID_GENERATOR.getAndIncrement();
            dispatcher = sharedState.dispatcher;
            burstCompleteField = sharedState.burstCompleteFields[threadId];
            messages = Arrays.copyOf(sharedState.messages, sharedState.messages.length);
            messages[messages.length - 1] = threadId;
            sendBuffer = new UnsafeBuffer(new byte[8]);
        }
    }

    @Benchmark
    @Threads(1)
    public void publishMessage1(ThreadState threadState)
    {
        sendBurst(threadState);
    }

    @Benchmark
    @Threads(2)
    public void publishMessage2(ThreadState threadState)
    {
        sendBurst(threadState);
    }

    @Benchmark
    @Threads(3)
    public void publishMessage3(ThreadState threadState)
    {
        sendBurst(threadState);
    }

    private static void sendBurst(ThreadState threadState)
    {
        final AtomicBoolean burstCompleteField = threadState.burstCompleteField;
        burstCompleteField.set(false);

        final int[] messages = threadState.messages;
        final Dispatcher dispatcher = threadState.dispatcher;
        final UnsafeBuffer sendBuffer = threadState.sendBuffer;

        for (int i = 0; i < messages.length; i++)
        {
            sendBuffer.putInt(0, messages[i]);
            while (dispatcher.offer(sendBuffer) < 0)
            {
                // spin
            }
        }

        while (!burstCompleteField.get())
        {
            // spin
        }
    }

}