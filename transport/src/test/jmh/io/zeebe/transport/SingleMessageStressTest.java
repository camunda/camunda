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

import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.ActorScheduler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
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
public class SingleMessageStressTest {
  private static final int BURST_SIZE = 1_000;

  private static final MutableDirectBuffer MSG = new UnsafeBuffer(new byte[576]);
  private static final BufferWriter WRITER = DirectBufferWriter.writerFor(MSG);

  @Benchmark
  @Threads(1)
  public void sendBurstSync(BenchmarkContext ctx) throws InterruptedException {
    final ClientOutput output = ctx.output;
    final int remoteId = ctx.remoteId;

    for (int i = 0; i < BURST_SIZE; i++) {
      while (!output.sendMessage(remoteId, WRITER)) {
        // spin
      }

      while (!ctx.messagesReceived.compareAndSet(1, 0)) {
        // spin
      }
    }
  }

  @Benchmark
  @Threads(1)
  public void sendBurstAsync(BenchmarkContext ctx) throws InterruptedException {
    ctx.messagesReceived.set(0);

    final ClientOutput output = ctx.output;
    final int remoteId = ctx.remoteId;

    int requestsSent = 0;

    do {
      if (requestsSent < BURST_SIZE) {
        if (output.sendMessage(remoteId, WRITER)) {
          requestsSent++;
        }
      }
    } while (ctx.messagesReceived.get() < BURST_SIZE);
  }

  @State(Scope.Benchmark)
  public static class BenchmarkContext implements ClientInputListener {
    private final ActorScheduler scheduler =
        ActorScheduler.newActorScheduler()
            .setIoBoundActorThreadCount(0)
            .setCpuBoundActorThreadCount(2)
            .build();

    private ClientTransport clientTransport;

    private ServerTransport serverTransport;

    private ClientOutput output;

    private int remoteId;

    private AtomicInteger messagesReceived = new AtomicInteger(0);

    @Setup
    public void setUp() {
      scheduler.start();

      final SocketAddress addr = SocketUtil.getNextAddress();

      clientTransport =
          Transports.newClientTransport("test").scheduler(scheduler).inputListener(this).build();

      serverTransport =
          Transports.newServerTransport()
              .bindAddress(addr.toInetSocketAddress())
              .scheduler(scheduler)
              .build(new EchoMessageHandler(), null);

      output = clientTransport.getOutput();
      remoteId = 1;

      clientTransport.registerEndpointAndAwaitChannel(remoteId, addr);
    }

    @TearDown
    public void tearDown() throws InterruptedException, ExecutionException, TimeoutException {
      serverTransport.close();
      clientTransport.close();
      scheduler.stop().get();
    }

    @Override
    public void onResponse(
        int streamId, long requestId, DirectBuffer buffer, int offset, int length) {}

    @Override
    public void onMessage(int streamId, DirectBuffer buffer, int offset, int length) {
      messagesReceived.incrementAndGet();
    }
  }
}
