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
package io.zeebe.raft;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

import io.zeebe.logstreams.log.*;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.impl.ServiceContainerImpl;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.collection.LongRingBuffer;
import io.zeebe.util.sched.ActorScheduler;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ThroughputTest
{
    private static final MutableDirectBuffer METADATA = new UnsafeBuffer(new byte[31]);
    private static final MutableDirectBuffer DATA = new UnsafeBuffer(new byte[256]);

    public static void main(String[] args) throws IOException
    {
        final BenchmarkContext ctx = new BenchmarkContext();
        ctx.setUp();

        final LogStreamWriter writer = ctx.writer;
        final LogStream logStream = ctx.leader.getLogStream();
        final LongRingBuffer uncommitedPositions = new LongRingBuffer(10 * 1024);

        while (true)
        {
            uncommitedPositions.consumeAscendingUntilInclusive(logStream.getCommitPosition());

            if (!uncommitedPositions.isSaturated())
            {
                final long position = writer
                    .positionAsKey()
                    .metadata(METADATA)
                    .value(DATA)
                    .tryWrite();

                if (position > 0)
                {
                    uncommitedPositions.addElementToHead(position);
                }
            }
            else
            {
                LockSupport.parkNanos(1000);
            }
        }
    }


    public static class BenchmarkContext
    {
        final ActorScheduler scheduler = ActorScheduler.newActorScheduler()
            .setIoBoundActorThreadCount(1)
            .setCpuBoundActorThreadCount(1)
            .build();

        final ServiceContainer serviceContainer = new ServiceContainerImpl(scheduler);

        final ThroughPutTestRaft raft1 = new ThroughPutTestRaft(new SocketAddress("localhost", 51015));
        final ThroughPutTestRaft raft2 = new ThroughPutTestRaft(new SocketAddress("localhost", 51016), raft1);
        final ThroughPutTestRaft raft3 = new ThroughPutTestRaft(new SocketAddress("localhost", 51017), raft1);

        final LogStreamWriter writer = new LogStreamWriterImpl();

        Raft leader;

        public void setUp() throws IOException
        {
            scheduler.start();
            serviceContainer.start();

            raft1.open(scheduler, serviceContainer);
            raft2.open(scheduler, serviceContainer);
            raft3.open(scheduler, serviceContainer);

            final List<Raft> rafts = Arrays.asList(raft1.getRaft(), raft2.getRaft(), raft3.getRaft());

            while (true)
            {
                final Optional<Raft> leader = rafts.stream().filter(r -> r.getState() == RaftState.LEADER).findAny();

                if (leader.isPresent()
                        && rafts.stream().filter(r -> r.getMemberSize() == 2).count() == 3
                        && rafts.stream().filter(r -> r.getState() == RaftState.FOLLOWER).count() == 2)
                {
                    this.leader = leader.get();
                    break;
                }
            }

            writer.wrap(leader.getLogStream());

            System.out.println("Leader: " + leader);
        }

        public void tearDown() throws InterruptedException, ExecutionException, TimeoutException
        {
            raft1.close();
            raft2.close();
            raft3.close();
            serviceContainer.close(5000, TimeUnit.SECONDS);
            scheduler.stop().get();
        }
    }

}
