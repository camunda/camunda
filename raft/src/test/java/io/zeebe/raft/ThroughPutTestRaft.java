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

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.raft.controller.MemberReplicateLogController;
import io.zeebe.raft.event.RaftConfigurationEvent;
import io.zeebe.raft.state.RaftState;
import io.zeebe.raft.util.InMemoryRaftPersistentStorage;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.*;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.channel.OneToOneRingBufferChannel;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

public class ThroughPutTestRaft implements RaftStateListener
{
    protected final RaftConfiguration configuration;
    protected final SocketAddress socketAddress;
    protected final String topicName;
    protected final int partition;
    protected final RaftConfigurationEvent configurationEvent = new RaftConfigurationEvent();
    protected final LogStreamWriterImpl writer = new LogStreamWriterImpl();
    protected final List<ThroughPutTestRaft> members;
    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();
    private final String name;

    protected ClientTransport clientTransport;
    protected Dispatcher clientSendBuffer;
    protected Dispatcher serverSendBuffer;
    protected ServerTransport serverTransport;

    protected LogStream logStream;
    protected Raft raft;
    private InMemoryRaftPersistentStorage persistentStorage;

    protected final List<RaftState> raftStateChanges = new ArrayList<>();
    private ServiceContainer serviceContainer;

    public ThroughPutTestRaft(SocketAddress socketAddress, ThroughPutTestRaft... members)
    {
        this.name = socketAddress.toString();
        this.configuration = new RaftConfiguration();
        this.topicName = "someTopic";
        this.partition = 0;
        this.members = Arrays.asList(members);
        this.socketAddress = socketAddress;
    }

    public void open(ActorScheduler scheduler, ServiceContainer serviceContainer) throws IOException
    {
        this.serviceContainer = serviceContainer;
        final RaftApiMessageHandler raftApiMessageHandler = new RaftApiMessageHandler();

        serverSendBuffer =
            Dispatchers.create("serverSendBuffer-" + name)
                       .bufferSize(8 * 1024 * 1024)
                       .actorScheduler(scheduler)
                       .build();

        serverTransport =
            Transports.newServerTransport()
                      .sendBuffer(serverSendBuffer)
                      .bindAddress(socketAddress.toInetSocketAddress())
                      .scheduler(scheduler)
                      .build(raftApiMessageHandler, raftApiMessageHandler);

        clientSendBuffer =
            Dispatchers.create("clientSendBuffer-" + name)
                       .bufferSize(8 * 1024 * 1024)
                       .actorScheduler(scheduler)
                       .build();

        clientTransport =
            Transports.newClientTransport()
                      .sendBuffer(clientSendBuffer)
                      .requestPoolSize(128)
                      .scheduler(scheduler)
                      .build();

        logStream =
            LogStreams.createFsLogStream(wrapString(topicName), partition)
                      .logName(String.format("%s-%d-%s", topicName, partition, socketAddress))
                      .deleteOnClose(true)
                      .logDirectory(Files.createTempDirectory("raft-test-" + socketAddress.port() + "-").toString())
                      .serviceContainer(serviceContainer)
                      .build()
                      .join();

        persistentStorage = new InMemoryRaftPersistentStorage(logStream);
        final OneToOneRingBufferChannel messageBuffer = new OneToOneRingBufferChannel(new UnsafeBuffer(new byte[(MemberReplicateLogController.REMOTE_BUFFER_SIZE) + RingBufferDescriptor.TRAILER_LENGTH]));

        raft = new Raft(logStream.getLogName(),
            configuration,
            socketAddress,
            clientTransport,
            persistentStorage,
            messageBuffer,
            this);
        raft.addMembersWhenJoined(this.members.stream().map(ThroughPutTestRaft::getSocketAddress).collect(Collectors.toList()));
        raftApiMessageHandler.registerRaft(raft);

        serviceContainer.createService(RaftServiceNames.raftServiceName(raft.getName()), raft)
            .install();
    }

    public void awaitWritable()
    {
        TestUtil.waitUntil(() -> logStream.getWriteBuffer() != null);
        writer.wrap(logStream);
    }

    public void close()
    {
        serviceContainer.removeService(RaftServiceNames.raftServiceName(raft.getName()));

        logStream.close();

        serverTransport.close();
        serverSendBuffer.close();

        clientTransport.close();
        clientSendBuffer.close();
    }

    public SocketAddress getSocketAddress()
    {
        return socketAddress;
    }

    public String getTopicName()
    {
        return topicName;
    }

    public int getPartition()
    {
        return partition;
    }

    public LogStreamWriterImpl getWriter()
    {
        return writer;
    }

    public List<ThroughPutTestRaft> getMembers()
    {
        return members;
    }

    public BrokerEventMetadata getMetadata()
    {
        return metadata;
    }

    public LogStream getLogStream()
    {
        return logStream;
    }

    public Raft getRaft()
    {
        return raft;
    }

    public InMemoryRaftPersistentStorage getPersistentStorage()
    {
        return persistentStorage;
    }

    public List<RaftState> getRaftStateChanges()
    {
        return raftStateChanges;
    }

    @Override
    public void onStateChange(Raft raft, RaftState raftState)
    {
        System.out.println(String.format("%s became %s", socketAddress, raftState));
    }
}
