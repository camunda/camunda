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

import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.raft.controller.MemberReplicateLogController;
import io.zeebe.raft.event.RaftConfigurationEvent;
import io.zeebe.raft.state.RaftState;
import io.zeebe.raft.util.InMemoryRaftPersistentStorage;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.channel.OneToOneRingBufferChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

public class ThroughPutTestRaft implements RaftStateListener {
  protected final RaftConfiguration configuration;
  protected final SocketAddress socketAddress;
  protected final int nodeId;
  protected final int partition;
  protected final RaftConfigurationEvent configurationEvent = new RaftConfigurationEvent();
  protected final LogStreamWriterImpl writer = new LogStreamWriterImpl();
  protected final List<ThroughPutTestRaft> members;
  protected final RecordMetadata metadata = new RecordMetadata();
  private final String name;

  protected ClientTransport clientTransport;
  protected ServerTransport serverTransport;

  protected LogStream logStream;
  protected Raft raft;
  private InMemoryRaftPersistentStorage persistentStorage;

  protected final List<RaftState> raftStateChanges = new ArrayList<>();
  private ServiceContainer serviceContainer;

  public ThroughPutTestRaft(final ThroughPutTestRaft... members) {
    this.socketAddress = SocketUtil.getNextAddress();
    this.name = socketAddress.toString();
    this.configuration = new RaftConfiguration();
    this.partition = 0;
    this.members = Arrays.asList(members);
    this.nodeId = socketAddress.port();
  }

  public void open(final ActorScheduler scheduler, final ServiceContainer serviceContainer)
      throws IOException {
    this.serviceContainer = serviceContainer;
    final RaftApiMessageHandler raftApiMessageHandler = new RaftApiMessageHandler();

    serverTransport =
        Transports.newServerTransport()
            .bindAddress(socketAddress.toInetSocketAddress())
            .scheduler(scheduler)
            .build(raftApiMessageHandler, raftApiMessageHandler);

    clientTransport = Transports.newClientTransport("test").scheduler(scheduler).build();

    final String logName = String.format("%d-%s", partition, socketAddress);
    final ServiceName<LogStream> logStreamServiceName =
        LogStreamServiceNames.logStreamServiceName(logName);
    logStream =
        LogStreams.createFsLogStream(partition)
            .logName(logName)
            .deleteOnClose(true)
            .logDirectory(
                Files.createTempDirectory("raft-test-" + socketAddress.port() + "-").toString())
            .serviceContainer(serviceContainer)
            .build()
            .join();

    persistentStorage = new InMemoryRaftPersistentStorage(logStream);
    final OneToOneRingBufferChannel messageBuffer =
        new OneToOneRingBufferChannel(
            new UnsafeBuffer(
                new byte
                    [(MemberReplicateLogController.REMOTE_BUFFER_SIZE)
                        + RingBufferDescriptor.TRAILER_LENGTH]));

    raft =
        new Raft(
            logStream.getLogName(),
            configuration,
            nodeId,
            clientTransport,
            persistentStorage,
            messageBuffer,
            this);
    raft.addMembersWhenJoined(
        this.members.stream().map(ThroughPutTestRaft::getNodeId).collect(Collectors.toList()));
    raftApiMessageHandler.registerRaft(raft);

    serviceContainer
        .createService(RaftServiceNames.raftServiceName(raft.getName()), raft)
        .dependency(logStreamServiceName, raft.getLogStreamInjector())
        .install();
  }

  public void awaitWritable() {
    TestUtil.waitUntil(() -> logStream.getWriteBuffer() != null);
    writer.wrap(logStream);
  }

  public void close() {
    serviceContainer.removeService(RaftServiceNames.raftServiceName(raft.getName()));

    logStream.close();

    serverTransport.close();
    clientTransport.close();
  }

  public SocketAddress getSocketAddress() {
    return socketAddress;
  }

  public int getNodeId() {
    return nodeId;
  }

  public int getPartition() {
    return partition;
  }

  public LogStreamWriterImpl getWriter() {
    return writer;
  }

  public List<ThroughPutTestRaft> getMembers() {
    return members;
  }

  public RecordMetadata getMetadata() {
    return metadata;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public Raft getRaft() {
    return raft;
  }

  public InMemoryRaftPersistentStorage getPersistentStorage() {
    return persistentStorage;
  }

  public List<RaftState> getRaftStateChanges() {
    return raftStateChanges;
  }

  @Override
  public void onStateChange(final Raft raft, final RaftState raftState) {
    System.out.println(String.format("%s became %s", socketAddress, raftState));
  }
}
