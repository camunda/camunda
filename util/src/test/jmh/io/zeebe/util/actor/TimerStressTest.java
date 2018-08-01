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
package io.zeebe.util.actor;

import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.ScheduledTimer;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class TimerStressTest {
  static final AtomicInteger THREAD_ID = new AtomicInteger(0);
  private static final int BURST_SIZE = 1_000;

  @Benchmark
  @Threads(1)
  public void shouldCancelTimers(BenchmarkContext ctx) throws InterruptedException {
    final ActorScheduler scheduler = ctx.scheduler;

    final CountDownLatch latch = new CountDownLatch(BURST_SIZE);

    for (int i = 0; i < BURST_SIZE; i++) {
      scheduler.submitActor(new AutoCancelTimerActor(latch));
    }

    latch.await();
  }

  @Benchmark
  @Threads(1)
  public void shoudRunConcurrentTimers(BenchmarkContext ctx) throws InterruptedException {
    final ActorScheduler scheduler = ctx.scheduler;

    final TimerActor actor = new TimerActor();
    scheduler.submitActor(actor).join();
    actor.close().join();
  }

  @State(Scope.Benchmark)
  public static class BenchmarkContext {
    ActorScheduler scheduler =
        ActorScheduler.newActorScheduler()
            .setIoBoundActorThreadCount(0)
            .setCpuBoundActorThreadCount(2)
            .build();

    @Setup
    public void setUp() {
      scheduler.start();
    }

    @TearDown
    public void tearDown() throws InterruptedException, ExecutionException, TimeoutException {
      scheduler.stop().get(2, TimeUnit.SECONDS);
    }
  }

  public static class AutoCancelTimerActor extends Actor {
    static Duration delay = Duration.ofSeconds(100);
    static Runnable callback = AutoCancelTimerActor::onTimeout;

    private final CountDownLatch latch;

    public AutoCancelTimerActor(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    protected void onActorStarted() {
      final ScheduledTimer timer = actor.runDelayed(delay, callback);
      timer.cancel();
      actor.close();
    }

    protected static void onTimeout() {
      // noop, never happens
    }

    @Override
    protected void onActorClosed() {
      latch.countDown();
    }
  }

  public static class TimerActor extends Actor {
    static Duration delay = Duration.ofSeconds(100);
    static Runnable callback = AutoCancelTimerActor::onTimeout;

    @Override
    protected void onActorStarted() {
      for (int i = 0; i < BURST_SIZE; i++) {
        actor.runDelayed(delay, callback);
      }
    }

    protected static void onTimeout() {
      // noop, never happens
    }

    public ActorFuture<Void> close() {
      return actor.close();
    }
  }
}
