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

import java.util.function.Consumer;

import io.zeebe.transport.*;
import io.zeebe.transport.impl.actor.Receiver;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;
import org.slf4j.Logger;

/**
 * Dispatches incoming requests and messages to the right raft.
 * This handler is called by the transport {@link Receiver} agent.
 */
public class RaftApiMessageHandler implements ServerMessageHandler, ServerRequestHandler, Consumer<Runnable>
{
    private static final Logger LOG = Loggers.RAFT_LOGGER;

    private final ManyToOneConcurrentLinkedQueue<Runnable> cmdQueue = new ManyToOneConcurrentLinkedQueue<>();
    private final Int2ObjectHashMap<Raft> raftMap = new Int2ObjectHashMap<>();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final RequestWithPartitionIdDecoder requestWithPartitionIdDecoder = new RequestWithPartitionIdDecoder();

    @Override
    public boolean onRequest(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length, long requestId)
    {
        drainCmdQueue();

        final int partitionId = getPartitionId(buffer, offset);
        final Raft raft = raftMap.get(partitionId);

        if (raft == null)
        {
            LOG.warn("Dropping request from {}: no partition with id {} present.", remoteAddress, partitionId);
        }
        else
        {
            raft.onRequest(output, remoteAddress, buffer, offset, length, requestId);
        }

        return true;
    }

    @Override
    public boolean onMessage(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
    {
        drainCmdQueue();

        final int partitionId = getPartitionId(buffer, offset);
        final Raft raft = raftMap.get(partitionId);

        if (raft == null)
        {
            LOG.warn("Dropping message from {}: no partition with id {} present.", remoteAddress, partitionId);
            return true;
        }
        else
        {
            return raft.onMessage(output, remoteAddress, buffer, offset, length);
        }
    }

    private int getPartitionId(DirectBuffer buffer, int offset)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        requestWithPartitionIdDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
        return requestWithPartitionIdDecoder.partitionId();
    }

    public void registerRaft(Raft raft)
    {
        cmdQueue.add(() -> raftMap.put(raft.getLogStream().getPartitionId(), raft));
    }

    public void removeRaft(Raft raft)
    {
        cmdQueue.add(() -> raftMap.remove(raft.getLogStream().getPartitionId()));
    }

    private void drainCmdQueue()
    {
        while (!cmdQueue.isEmpty())
        {
            final Runnable r = cmdQueue.poll();
            if (r != null)
            {
                r.run();
            }
        }
    }

    @Override
    public void accept(Runnable t)
    {
        t.run();
    }
}
