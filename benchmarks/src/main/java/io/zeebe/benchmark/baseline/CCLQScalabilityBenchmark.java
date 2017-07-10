/**
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.benchmark.baseline;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.LangUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

public class CCLQScalabilityBenchmark
{
    static final AtomicInteger THREAD_ID_GENERATOR = new AtomicInteger(0);
    private static final int BURST_SIZE = Integer.getInteger("burst.size", 1);

    @State(Scope.Benchmark)
    public static class SharedState
    {
        ConcurrentLinkedQueue<Integer> queue;
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

            queue = new ConcurrentLinkedQueue<>();

            consumer = new Thread(() ->
            {
                do
                {
                    final Integer result = queue.poll();

                    if (result != null && result >= 0)
                    {
                        burstCompleteFields[result].set(true);
                    }
                }
                while (!exit);
            });

            consumer.start();
        }

        @TearDown
        public void stop()
        {
            exit = true;

            try
            {
                consumer.join();
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
        protected ConcurrentLinkedQueue<Integer> queue;
        protected AtomicBoolean burstCompleteField;
        protected int[] messages;

        @Setup
        public void setup(final SharedState sharedState)
        {
            threadId = THREAD_ID_GENERATOR.getAndIncrement();
            queue = sharedState.queue;
            burstCompleteField = sharedState.burstCompleteFields[threadId];
            messages = Arrays.copyOf(sharedState.messages, sharedState.messages.length);
            messages[messages.length - 1] = threadId;
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
        final ConcurrentLinkedQueue<Integer> queue = threadState.queue;

        for (int i = 0; i < messages.length; i++)
        {
            while (!queue.offer(messages[i]))
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