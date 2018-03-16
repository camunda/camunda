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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.util.sched.ActorScheduler;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class SingleMessageStressTest
{
    static final AtomicInteger THREAD_ID = new AtomicInteger(0);
    private static final int BURST_SIZE = 1_000;

    private static final int MAX_CONCURRENT_REQUESTS = 128;
    private static final MutableDirectBuffer MSG = new UnsafeBuffer(new byte[576]);

    @Benchmark
    @Threads(1)
    public void sendBurstSync(BenchmarkContext ctx) throws InterruptedException
    {
        final ClientOutput output = ctx.output;
        final RemoteAddress remote = ctx.remote;
        final TransportMessage message = ctx.transportMessage;

        for (int i = 0; i < BURST_SIZE; i++)
        {
            message.reset().remoteAddress(remote)
                .buffer(MSG);

            while (!output.sendMessage(message))
            {
                // spin
            }

            while (!ctx.messagesReceived.compareAndSet(1, 0))
            {
                // spin
            }
        }
    }

    @Benchmark
    @Threads(1)
    public void sendBurstAsync(BenchmarkContext ctx) throws InterruptedException
    {
        ctx.messagesReceived.set(0);

        final ClientOutput output = ctx.output;
        final RemoteAddress remote = ctx.remote;
        final TransportMessage message = ctx.transportMessage;

        int requestsSent = 0;

        do
        {
            if (requestsSent < BURST_SIZE)
            {
                message.reset()
                    .remoteAddress(remote)
                    .buffer(MSG);

                if (output.sendMessage(message))
                {
                    requestsSent++;
                }
            }
        }
        while (ctx.messagesReceived.get() < BURST_SIZE);
    }

    @State(Scope.Benchmark)
    public static class BenchmarkContext implements ClientInputListener
    {
        private final TransportMessage transportMessage = new TransportMessage();

        private final ActorScheduler scheduler = ActorScheduler.newActorScheduler()
            .setIoBoundActorThreadCount(0)
            .setCpuBoundActorThreadCount(2)
            .build();

        private Dispatcher clientSendBuffer;

        private Dispatcher serverSendBuffer;

        private ClientTransport clientTransport;

        private ServerTransport serverTransport;

        private ClientOutput output;

        private RemoteAddress remote;

        private AtomicInteger messagesReceived = new AtomicInteger(0);

        @Setup
        public void setUp()
        {
            scheduler.start();

            final SocketAddress addr = new SocketAddress("localhost", 51115);

            clientSendBuffer = Dispatchers.create("clientSendBuffer")
                .bufferSize(32 * 1024 * 1024)
                .actorScheduler(scheduler)
                .build();

            serverSendBuffer = Dispatchers.create("serverSendBuffer")
                .bufferSize(32 * 1024 * 1024)
                .actorScheduler(scheduler)
                .build();

            clientTransport = Transports.newClientTransport()
                .sendBuffer(clientSendBuffer)
                .requestPoolSize(MAX_CONCURRENT_REQUESTS)
                .scheduler(scheduler)
                .inputListener(this)
                .build();

            serverTransport = Transports.newServerTransport()
                .sendBuffer(serverSendBuffer)
                .bindAddress(addr.toInetSocketAddress())
                .scheduler(scheduler)
                .build(new EchoMessageHandler(), null);

            output = clientTransport.getOutput();

            remote = clientTransport.registerRemoteAndAwaitChannel(addr);
        }

        @TearDown
        public void tearDown() throws InterruptedException, ExecutionException, TimeoutException
        {
            serverTransport.close();
            clientTransport.close();
            serverSendBuffer.close();
            clientSendBuffer.close();
            scheduler.stop().get();
        }

        @Override
        public void onResponse(int streamId, long requestId, DirectBuffer buffer, int offset, int length)
        {

        }

        @Override
        public void onMessage(int streamId, DirectBuffer buffer, int offset, int length)
        {
            messagesReceived.incrementAndGet();
        }
    }
}
