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
import io.zeebe.util.sched.future.ActorFuture;
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
public class FuturesStressTest {
  static final AtomicInteger THREAD_ID = new AtomicInteger(0);
  private static final int BURST_SIZE = 1_000;

  @Benchmark
  @Threads(1)
  @SuppressWarnings("rawtypes")
  public void ping(BenchmarkContext ctx) throws InterruptedException {
    final PingActor pingActor = ctx.pingActor;
    final ActorFuture[] futures = new ActorFuture[BURST_SIZE];

    for (int i = 0; i < BURST_SIZE; i++) {
      futures[i] = pingActor.ping();
    }

    for (int i = 0; i < BURST_SIZE; i++) {
      futures[i].join();
    }
  }

  @Benchmark
  @Threads(1)
  public void pingPong(BenchmarkContext ctx) throws InterruptedException {
    final ActorScheduler scheduler = ctx.scheduler;
    final CountDownLatch latch = new CountDownLatch(BURST_SIZE);

    for (int i = 0; i < BURST_SIZE; i++) {
      scheduler.submitActor(new PongActor(ctx.pingActor, latch));
    }

    latch.await();
  }

  @Benchmark
  @Threads(1)
  public void getScalability1Threads(BenchmarkContext ctx) throws InterruptedException {
    ctx.pingActor.ping().join();
  }

  @Benchmark
  @Threads(4)
  public void getScalability4Threads(BenchmarkContext ctx) throws InterruptedException {
    ctx.pingActor.ping().join();
  }

  @Benchmark
  @Threads(8)
  public void getScalability8Threads(BenchmarkContext ctx) throws InterruptedException {
    ctx.pingActor.ping().join();
  }

  @State(Scope.Benchmark)
  public static class BenchmarkContext {
    ActorScheduler scheduler =
        ActorScheduler.newActorScheduler()
            .setIoBoundActorThreadCount(0)
            .setCpuBoundActorThreadCount(2)
            .build();

    PingActor pingActor = new PingActor();

    @Setup
    public void setUp() {
      scheduler.start();
      scheduler.submitActor(pingActor).join();
    }

    @TearDown
    public void tearDown() throws InterruptedException, ExecutionException, TimeoutException {
      pingActor.close().join();
      scheduler.stop().get(2, TimeUnit.SECONDS);
    }
  }

  static class PingActor extends Actor {
    public ActorFuture<Void> ping(PongActor pongActor) {
      return actor.call(
          () -> {
            pongActor.pong();
          });
    }

    public ActorFuture<Void> ping() {
      return actor.call(() -> {});
    }

    public ActorFuture<Void> close() {
      return actor.close();
    }
  }

  static class PongActor extends Actor {
    private PingActor pingActor;
    private CountDownLatch latch;

    PongActor(PingActor pingActor, CountDownLatch latch) {
      this.pingActor = pingActor;
      this.latch = latch;
    }

    @Override
    protected void onActorStarting() {
      pingActor.ping(this);
    }

    public void pong() {
      actor.call(
          () -> {
            actor.close();
          });
    }

    @Override
    protected void onActorClosed() {
      latch.countDown();
    }
  }
}
