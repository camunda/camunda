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
package io.zeebe.transport.test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.transport.*;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class RequestResponseStressTest
{
    static final AtomicInteger THREAD_ID = new AtomicInteger(0);
    private static final int BURST_SIZE = 1_000;

    private static final int MAX_CONCURRENT_REQUESTS = 128;
    private static final MutableDirectBuffer MSG = new UnsafeBuffer(new byte[576]);
    private static final DirectBufferWriter WRITER = new DirectBufferWriter().wrap(MSG);

    @Benchmark
    @Threads(1)
    public void sendBurstSync(BenchmarkContext ctx) throws InterruptedException
    {
        final ClientOutput output = ctx.output;
        final RemoteAddress remote = ctx.remote;

        for (int i = 0; i < BURST_SIZE; i++)
        {
            final ActorFuture<ClientResponse> responseFuture = output.sendRequest(remote, WRITER);

            try (ClientResponse response = responseFuture.join())
            {
                final DirectBuffer responseBuffer = response.getResponseBuffer();
                // do assert?
            }
        }
    }

    @Benchmark
    @Threads(2)
    public void sendBurstSync2Threads(BenchmarkContext ctx) throws InterruptedException
    {
        final ClientOutput output = ctx.output;
        final RemoteAddress remote = ctx.remote;

        for (int i = 0; i < BURST_SIZE; i++)
        {
            final ActorFuture<ClientResponse> responseFuture = output.sendRequest(remote, WRITER);

            try (ClientResponse response = responseFuture.join())
            {
                final DirectBuffer responseBuffer = response.getResponseBuffer();
                // do assert?
            }
        }
    }

    @Benchmark
    @Threads(1)
    public void sendBurstAsync(BenchmarkContext ctx) throws InterruptedException
    {
        final ClientOutput output = ctx.output;
        final RemoteAddress remote = ctx.remote;

        final List<ActorFuture<ClientResponse>> inFlightRequests = new ArrayList<>();

        int requestsSent = 0;
        int responsesReceived = 0;

        do
        {
            final int requestsInFlight = requestsSent - responsesReceived;
            if (requestsInFlight < MAX_CONCURRENT_REQUESTS && requestsSent < BURST_SIZE)
            {
                inFlightRequests.add(output.sendRequest(remote, WRITER));
                requestsSent++;
            }

            final Iterator<ActorFuture<ClientResponse>> responseIterator = inFlightRequests.iterator();
            while (responseIterator.hasNext())
            {
                final ActorFuture<io.zeebe.transport.ClientResponse> responseFuture = responseIterator.next();

                if (responseFuture.isDone())
                {
                    responsesReceived++;
                    responseIterator.remove();

                    try (ClientResponse response = responseFuture.join())
                    {
                        final DirectBuffer responseBuffer = response.getResponseBuffer();
                        // do assert?
                    }
                }
            }

        }
        while (responsesReceived < BURST_SIZE);
    }

    @State(Scope.Benchmark)
    public static class BenchmarkContext
    {


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
                .build();

            serverTransport = Transports.newServerTransport()
                .sendBuffer(serverSendBuffer)
                .bindAddress(addr.toInetSocketAddress())
                .scheduler(scheduler)
                .build(null, new EchoRequestResponseHandler());

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
    }
}
