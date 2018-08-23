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
package io.zeebe.transport;

import io.zeebe.transport.impl.memory.BlockingMemoryPool;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.ByteValue;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
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
public class RequestResponseStressTest {
  static final AtomicInteger THREAD_ID = new AtomicInteger(0);
  private static final int BURST_SIZE = 1_000;

  private static final MutableDirectBuffer MSG = new UnsafeBuffer(new byte[576]);
  private static final DirectBufferWriter WRITER = new DirectBufferWriter().wrap(MSG);

  @Benchmark
  @Threads(1)
  public void sendBurstSync1(BenchmarkContext ctx) throws InterruptedException {
    final ClientOutput output = ctx.output;
    final int remote = ctx.remote;

    for (int i = 0; i < BURST_SIZE; i++) {
      final ActorFuture<ClientResponse> responseFuture = output.sendRequest(remote, WRITER);
      responseFuture.join();
    }
  }

  @Benchmark
  @Threads(2)
  public void sendBurstSync2(BenchmarkContext ctx) throws InterruptedException {
    final ClientOutput output = ctx.output;
    final int remote = ctx.remote;

    for (int i = 0; i < BURST_SIZE; i++) {
      final ActorFuture<ClientResponse> responseFuture = output.sendRequest(remote, WRITER);
      responseFuture.join();
    }
  }

  @Benchmark
  @Threads(8)
  public void sendBurstSync8(BenchmarkContext ctx) throws InterruptedException {
    final ClientOutput output = ctx.output;
    final int remote = ctx.remote;

    for (int i = 0; i < BURST_SIZE; i++) {
      final ActorFuture<ClientResponse> responseFuture = output.sendRequest(remote, WRITER);
      responseFuture.join();
    }
  }

  @Benchmark
  @Threads(16)
  public void sendBurstSync16(BenchmarkContext ctx) throws InterruptedException {
    final ClientOutput output = ctx.output;
    final int remote = ctx.remote;

    for (int i = 0; i < BURST_SIZE; i++) {
      final ActorFuture<ClientResponse> responseFuture = output.sendRequest(remote, WRITER);
      responseFuture.join();
    }
  }

  @Benchmark
  @Threads(32)
  public void sendBurstSync32(BenchmarkContext ctx) throws InterruptedException {
    final ClientOutput output = ctx.output;
    final int remote = ctx.remote;

    for (int i = 0; i < BURST_SIZE; i++) {
      final ActorFuture<ClientResponse> responseFuture = output.sendRequest(remote, WRITER);
      responseFuture.join();
    }
  }

  @Benchmark
  @Threads(1)
  public void sendBurstAsync(BenchmarkContext ctx) throws InterruptedException {
    final ClientOutput output = ctx.output;
    final int remote = ctx.remote;

    for (int k = 0; k < 4; k++) {
      final List<ActorFuture<ClientResponse>> inFlightRequests = new ArrayList<>();

      for (int i = 0; i < BURST_SIZE / 4; i++) {
        inFlightRequests.add(output.sendRequest(remote, WRITER));
      }

      inFlightRequests.forEach(ActorFuture::join);
    }
  }

  @State(Scope.Benchmark)
  public static class BenchmarkContext {
    private final ActorScheduler scheduler =
        ActorScheduler.newActorScheduler()
            .setIoBoundActorThreadCount(0)
            .setCpuBoundActorThreadCount(2)
            .build();

    private ClientTransport clientTransport;

    private ServerTransport serverTransport;

    private ClientOutput output;

    private int remote;

    @Setup
    public void setUp() {
      scheduler.start();

      final SocketAddress addr = SocketUtil.getNextAddress();

      clientTransport =
          Transports.newClientTransport("test")
              .scheduler(scheduler)
              .requestMemoryPool(new BlockingMemoryPool(ByteValue.ofMegabytes(4), 1000))
              .build();

      serverTransport =
          Transports.newServerTransport()
              .bindAddress(addr.toInetSocketAddress())
              .scheduler(scheduler)
              .build(null, new EchoRequestResponseHandler());

      output = clientTransport.getOutput();

      remote = 1;
      clientTransport.registerEndpointAndAwaitChannel(remote, addr);
    }

    @TearDown
    public void tearDown() throws InterruptedException, ExecutionException, TimeoutException {
      serverTransport.close();
      clientTransport.close();
      scheduler.stop().get();
    }
  }
}
