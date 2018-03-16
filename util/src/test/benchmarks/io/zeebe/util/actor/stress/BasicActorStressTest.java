/*
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
package io.zeebe.util.actor.stress;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class BasicActorStressTest
{
    static final AtomicInteger THREAD_ID = new AtomicInteger(0);
    private static final int BURST_SIZE = 1_000;

    @Benchmark
    @Threads(1)
    public void shouldRunActors1Thread(BenchmarkContext ctx) throws InterruptedException
    {
        sendBurst(ctx);
    }

    @Benchmark
    @Threads(2)
    public void shouldRunActors2Threads(BenchmarkContext ctx) throws InterruptedException
    {
        sendBurst(ctx);
    }

    private void sendBurst(BenchmarkContext ctx) throws InterruptedException
    {
        final ActorScheduler scheduler = ctx.scheduler;
        final CountDownLatch latch = new CountDownLatch(BURST_SIZE);

        for (int i = 0; i < BURST_SIZE; i++)
        {
            scheduler.submitActor(new ClosableActor(latch));
        }

        latch.await();
    }

    @State(Scope.Benchmark)
    public static class BenchmarkContext
    {
        ActorScheduler scheduler = ActorScheduler.newActorScheduler()
            .setIoBoundActorThreadCount(0)
            .setCpuBoundActorThreadCount(2)
            .build();

        @Setup
        public void setUp()
        {
            scheduler.start();
        }

        @TearDown
        public void tearDown() throws InterruptedException, ExecutionException, TimeoutException
        {
            scheduler.stop().get(2, TimeUnit.SECONDS);
        }
    }


    static class ClosableActor extends Actor
    {
        private CountDownLatch latch;

        ClosableActor(CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        protected void onActorStarted()
        {
            actor.close();
        }

        @Override
        protected void onActorClosed()
        {
            latch.countDown();
        }
    }
}
