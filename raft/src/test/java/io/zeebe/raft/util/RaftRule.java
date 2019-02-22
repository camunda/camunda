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
package io.zeebe.raft.util;

import static io.zeebe.raft.RaftServiceNames.raftServiceName;
import static io.zeebe.raft.state.RaftState.LEADER;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.raft.Loggers;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftApiMessageHandler;
import io.zeebe.raft.RaftConfiguration;
import io.zeebe.raft.RaftStateListener;
import io.zeebe.raft.controller.MemberReplicateLogController;
import io.zeebe.raft.event.RaftConfigurationEvent;
import io.zeebe.raft.event.RaftConfigurationEventMember;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.LogUtil;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.channel.OneToOneRingBufferChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.junit.rules.ExternalResource;

public class RaftRule extends ExternalResource implements RaftStateListener {

  protected final ServiceContainer serviceContainer;
  protected final ActorScheduler actorScheduler;
  protected final RaftConfiguration configuration;
  protected final SocketAddress socketAddress;
  protected final int nodeId;
  protected final int partition;
  protected final RaftConfigurationEvent configurationEvent = new RaftConfigurationEvent();
  protected final LogStreamWriterImpl writer = new LogStreamWriterImpl();
  protected final List<RaftRule> members;
  protected final RecordMetadata metadata = new RecordMetadata();

  protected ClientTransport clientTransport;

  protected ServerTransport serverTransport;

  protected LogStream logStream;
  protected Raft raft;
  protected BufferedLogStreamReader uncommittedReader;
  protected BufferedLogStreamReader committedReader;

  private InMemoryRaftPersistentStorage persistentStorage;

  protected final List<RaftState> raftStateChanges = new ArrayList<>();
  private ServiceName<Raft> raftServiceName;

  public RaftRule(
      final ServiceContainerRule serviceContainerRule,
      final int nodeId,
      final int partition,
      final RaftRule... members) {
    this.actorScheduler = serviceContainerRule.getActorScheduler();
    this.serviceContainer = serviceContainerRule.get();
    this.configuration = new RaftConfiguration().setLeaveTimeout("10s");
    this.nodeId = nodeId;
    this.socketAddress = SocketUtil.getNextAddress();

    this.partition = partition;
    this.members = members != null ? Arrays.asList(members) : Collections.emptyList();
  }

  @Override
  protected void before() throws Throwable {
    final String logName = String.format("%d-%d", partition, nodeId);

    final RaftApiMessageHandler raftApiMessageHandler = new RaftApiMessageHandler();

    serverTransport =
        Transports.newServerTransport()
            .bindAddress(socketAddress.toInetSocketAddress())
            .scheduler(actorScheduler)
            .build(raftApiMessageHandler, raftApiMessageHandler);

    clientTransport =
        Transports.newClientTransport("raft-" + nodeId).scheduler(actorScheduler).build();

    logStream =
        LogStreams.createFsLogStream(partition)
            .logName(logName)
            .deleteOnClose(true)
            .logDirectory(Files.createTempDirectory("raft-test-" + nodeId + "-").toString())
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
            logName,
            configuration,
            nodeId,
            clientTransport,
            persistentStorage,
            messageBuffer,
            this);
    raftApiMessageHandler.registerRaft(raft);
    raft.addMembersWhenJoined(
        members.stream().map(RaftRule::getNodeId).collect(Collectors.toList()));

    uncommittedReader = new BufferedLogStreamReader(logStream);
    committedReader = new BufferedLogStreamReader(logStream);

    raftServiceName = raftServiceName(logName);
    serviceContainer
        .createService(raftServiceName, raft)
        .dependency(
            LogStreamServiceNames.logStreamServiceName(logName), raft.getLogStreamInjector())
        .install()
        .join();
  }

  @Override
  protected void after() {
    if (serviceContainer.hasService(raftServiceName)) {
      closeRaft();
    }

    try {
      logStream.closeAsync().get(30, TimeUnit.SECONDS);
    } catch (final Exception e) {
      e.printStackTrace();
    }

    serverTransport.close();
    clientTransport.close();

    uncommittedReader.close();
    committedReader.close();
  }

  public void closeRaft() {
    LogUtil.catchAndLog(
        Loggers.RAFT_LOGGER,
        () -> serviceContainer.removeService(raftServiceName).get(5, TimeUnit.SECONDS));
  }

  public boolean isClosed() {
    return !serviceContainer.hasService(raftServiceName);
  }

  public int getNodeId() {
    return nodeId;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public int getTerm() {
    return raft.getTerm();
  }

  public Raft getRaft() {
    return raft;
  }

  public int getMemberSize() {
    return raft.getMemberSize();
  }

  public RaftConfiguration getConfiguration() {
    return raft.getConfiguration();
  }

  public RaftState getState() {
    return raft.getState();
  }

  @Override
  public void onStateChange(final Raft raft, final RaftState raftState) {
    final int partitionId = raft.getPartitionId();
    final int nodeId = raft.getNodeId();

    assertThat(partitionId).isEqualTo(this.logStream.getPartitionId());
    assertThat(nodeId).isEqualTo(this.nodeId);

    raftStateChanges.add(raftState);
  }

  public List<RaftState> getRaftStateChanges() {
    return raftStateChanges;
  }

  public InMemoryRaftPersistentStorage getPersistentStorage() {
    return persistentStorage;
  }

  public void clearSubscription() {
    raft.clearReceiveBuffer().join();
  }

  public boolean isLeader() {
    return getState() == LEADER;
  }

  public boolean isJoined() {
    return raft.isJoined();
  }

  public EventInfo writeEvent(final String message) {
    writer.wrap(logStream);

    final EventInfo[] eventInfo = new EventInfo[1];

    final DirectBuffer value = wrapString(message);

    TestUtil.doRepeatedly(
            () -> writer.positionAsKey().metadataWriter(metadata.reset()).value(value).tryWrite())
        .until(
            position -> {
              if (position != null && position >= 0) {
                eventInfo[0] = new EventInfo(position, raft.getTerm(), message);
                return true;
              } else {
                return false;
              }
            },
            "Failed to write event with message {}",
            message);

    return eventInfo[0];
  }

  public EventInfo writeEvents(final String... messages) {
    EventInfo eventInfo = null;

    for (final String message : messages) {
      eventInfo = writeEvent(message);
    }

    return eventInfo;
  }

  public boolean eventAppended(final EventInfo eventInfo) {
    uncommittedReader.seek(eventInfo.getPosition());

    if (uncommittedReader.hasNext()) {
      final EventInfo event = new EventInfo(uncommittedReader.next());
      return event.equals(eventInfo);
    }

    return false;
  }

  public boolean eventCommitted(final EventInfo eventInfo) {
    final boolean isCommitted = logStream.getCommitPosition() >= eventInfo.getPosition();

    return isCommitted && eventAppended(eventInfo);
  }

  public boolean eventsCommitted(final String... messages) {
    committedReader.seekToFirstEvent();

    final RecordMetadata metadata = new RecordMetadata();

    return Arrays.stream(messages)
        .allMatch(
            message -> {
              while (committedReader.hasNext()) {
                final LoggedEvent event = committedReader.next();
                event.readMetadata(metadata);

                if (metadata.getValueType() == ValueType.NULL_VAL) {
                  try {
                    final String value =
                        bufferAsString(
                            event.getValueBuffer(), event.getValueOffset(), event.getValueLength());

                    return message.equals(value);
                  } catch (final Exception e) {
                    // ignore
                  }
                }
              }

              return false;
            });
  }

  public boolean eventCommitted(final int term, final ValueType eventType) {
    committedReader.seekToFirstEvent();

    while (committedReader.hasNext()) {
      final LoggedEvent event = committedReader.next();
      event.readMetadata(metadata);

      if (event.getRaftTerm() == term && metadata.getValueType() == eventType) {
        return true;
      } else if (event.getRaftTerm() > term) {
        // early abort which assumes that terms are always increasing
        return false;
      }
    }

    return false;
  }

  public boolean raftEventCommitted(final int term, final RaftRule... members) {
    committedReader.seekToFirstEvent();

    final Set<Integer> expected;

    if (members == null) {
      expected = Collections.emptySet();
    } else {
      expected = Arrays.stream(members).map(RaftRule::getNodeId).collect(Collectors.toSet());
    }

    while (committedReader.hasNext()) {
      final LoggedEvent event = committedReader.next();
      event.readMetadata(metadata);

      if (event.getRaftTerm() == term && metadata.getValueType() == ValueType.RAFT) {
        event.readValue(configurationEvent);

        final Iterator<RaftConfigurationEventMember> configurationMembers =
            configurationEvent.members().iterator();

        final Set<Integer> found = new HashSet<>();

        while (configurationMembers.hasNext()) {
          final RaftConfigurationEventMember configurationMember = configurationMembers.next();
          found.add(configurationMember.getNodeId());
        }

        if (expected.equals(found)) {
          return true;
        }
      } else if (event.getRaftTerm() > term) {
        // early abort which assumes that terms are always increasing
        return false;
      }
    }

    return false;
  }

  @Override
  public String toString() {
    return raft.toString();
  }

  public void interruptConnectionTo(final RaftRule other) {
    clientTransport.deactivateEndpoint(other.getNodeId());
    other.clientTransport.deactivateEndpoint(this.getNodeId());
  }

  public void reconnectTo(final RaftRule other) {
    clientTransport.registerEndpoint(other.getNodeId(), other.socketAddress);
    other.clientTransport.registerEndpoint(this.getNodeId(), this.socketAddress);
  }
}
