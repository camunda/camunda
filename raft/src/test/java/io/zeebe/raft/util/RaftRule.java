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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
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
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.RequestTimeoutException;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.TransportMessage;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.LogUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.channel.OneToOneRingBufferChannel;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.lang.reflect.Field;
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
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class RaftRule extends ExternalResource implements RaftStateListener {

  public static final String DEFAULT_HOST = "localhost";
  public static final int DEFAULT_PORT = 9000;

  public static final FragmentHandler NOOP_FRAGMENT_HANDLER =
      (buffer, offset, length, streamId, isMarkedFailed) -> FragmentHandler.CONSUME_FRAGMENT_RESULT;

  protected final ServiceContainer serviceContainer;
  protected final ActorScheduler actorScheduler;
  protected final RaftConfiguration configuration;
  protected final SocketAddress socketAddress;
  protected final String topicName;
  protected final int partition;
  protected final RaftConfigurationEvent configurationEvent = new RaftConfigurationEvent();
  protected final LogStreamWriterImpl writer = new LogStreamWriterImpl();
  protected final List<RaftRule> members;
  protected final RecordMetadata metadata = new RecordMetadata();

  protected ClientTransport clientTransport;
  protected ClientOutput spyClientOutput;

  protected ServerTransport serverTransport;

  protected LogStream logStream;
  protected Raft raft;
  private ActorControl raftActor;
  protected BufferedLogStreamReader uncommittedReader;
  protected BufferedLogStreamReader committedReader;

  private InMemoryRaftPersistentStorage persistentStorage;

  protected final List<RaftState> raftStateChanges = new ArrayList<>();
  protected Set<Integer> interrupedStreams = Collections.synchronizedSet(new HashSet<>());
  private ServiceName<Raft> raftServiceName;

  public RaftRule(
      final ServiceContainerRule serviceContainerRule,
      final String topicName,
      final int partition,
      final RaftRule... members) {
    this(
        serviceContainerRule,
        new RaftConfiguration().setLeaveTimeout("10s"),
        topicName,
        partition,
        members);
  }

  public RaftRule(
      final ServiceContainerRule serviceContainerRule,
      final RaftConfiguration configuration,
      final String topicName,
      final int partition,
      final RaftRule... members) {
    this.actorScheduler = serviceContainerRule.getActorScheduler();
    this.serviceContainer = serviceContainerRule.get();
    this.configuration = configuration;
    this.socketAddress = SocketUtil.getNextAddress();

    this.topicName = topicName;
    this.partition = partition;
    this.members = members != null ? Arrays.asList(members) : Collections.emptyList();
  }

  @Override
  protected void before() throws Throwable {
    final String name = socketAddress.toString();
    final String logName = String.format("%s-%d-%d", topicName, partition, socketAddress.port());

    final RaftApiMessageHandler raftApiMessageHandler = new RaftApiMessageHandler();

    serverTransport =
        Transports.newServerTransport()
            .bindAddress(socketAddress.toInetSocketAddress())
            .scheduler(actorScheduler)
            .build(raftApiMessageHandler, raftApiMessageHandler);

    clientTransport = Transports.newClientTransport().scheduler(actorScheduler).build();

    logStream =
        LogStreams.createFsLogStream(wrapString(topicName), partition)
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

    spyClientOutput = spy(clientTransport.getOutput());
    final ClientTransport spyClientTransport = spy(clientTransport);
    when(spyClientTransport.getOutput()).thenReturn(spyClientOutput);

    doAnswer(
            new Answer<Boolean>() {
              @Override
              public Boolean answer(InvocationOnMock invocation) throws Throwable {
                final TransportMessage msg = invocation.getArgument(0);
                final int stream = readRemoteStreamId(msg);

                if (interrupedStreams.contains(stream)) {
                  return true;
                } else {
                  return (Boolean) invocation.callRealMethod();
                }
              }
            })
        .when(spyClientOutput)
        .sendMessage(any(TransportMessage.class));

    raft =
        new Raft(
            logName,
            configuration,
            socketAddress,
            spyClientTransport,
            persistentStorage,
            messageBuffer,
            this) {
          @Override
          protected void onActorStarting() {
            raftActor = actor;
            super.onActorStarting();
          }
        };
    raftApiMessageHandler.registerRaft(raft);
    raft.addMembersWhenJoined(
        members.stream().map(RaftRule::getSocketAddress).collect(Collectors.toList()));

    uncommittedReader = new BufferedLogStreamReader(logStream, true);
    committedReader = new BufferedLogStreamReader(logStream, false);

    raftServiceName = raftServiceName(name);
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

    logStream.close();

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

  public SocketAddress getSocketAddress() {
    return socketAddress;
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
  public void onStateChange(Raft raft, RaftState raftState) {
    final int partitionId = raft.getPartitionId();
    final DirectBuffer topicName = raft.getTopicName();
    final SocketAddress socketAddress = raft.getSocketAddress();

    assertThat(partitionId).isEqualTo(raft.getLogStream().getPartitionId());
    assertThat(topicName).isEqualByComparingTo(raft.getLogStream().getTopicName());
    assertThat(socketAddress).isEqualTo(raft.getSocketAddress());

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

    final Set<SocketAddress> expected;

    if (members == null) {
      expected = Collections.emptySet();
    } else {
      expected = Arrays.stream(members).map(RaftRule::getSocketAddress).collect(Collectors.toSet());
    }

    while (committedReader.hasNext()) {
      final LoggedEvent event = committedReader.next();
      event.readMetadata(metadata);

      if (event.getRaftTerm() == term && metadata.getValueType() == ValueType.RAFT) {
        event.readValue(configurationEvent);

        final Iterator<RaftConfigurationEventMember> configurationMembers =
            configurationEvent.members().iterator();

        final Set<SocketAddress> found = new HashSet<>();

        while (configurationMembers.hasNext()) {
          final RaftConfigurationEventMember configurationMember = configurationMembers.next();
          found.add(configurationMember.getSocketAddress());
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

  public void interruptConnectionTo(RaftRule other) {
    raftActor
        .call(
            () -> {
              final ArgumentMatcher<RemoteAddress> remoteAddressMatcher =
                  r -> other.socketAddress.equals(r.getAddress());

              doReturn(
                      CompletableActorFuture.completedExceptionally(
                          new RequestTimeoutException("connection is interrupted")))
                  .when(spyClientOutput)
                  .sendRequest(argThat(remoteAddressMatcher), any());

              doReturn(
                      CompletableActorFuture.completedExceptionally(
                          new RequestTimeoutException("timeout to " + other.socketAddress)))
                  .when(spyClientOutput)
                  .sendRequest(argThat(remoteAddressMatcher), any(), any());

              final RemoteAddress remoteAddress =
                  clientTransport.registerRemoteAddress(other.getSocketAddress());
              interrupedStreams.add(remoteAddress.getStreamId());
            })
        .join();
  }

  private int readRemoteStreamId(TransportMessage transportMessage) {
    final Class<TransportMessage> transportMessageClass = TransportMessage.class;
    int value;
    try {
      final Field remoteStreamId = transportMessageClass.getDeclaredField("remoteStreamId");
      remoteStreamId.setAccessible(true);
      value = (int) remoteStreamId.get(transportMessage);
    } catch (Exception e) {
      value = -1;
    }
    return value;
  }

  public void reconnectTo(RaftRule other) {
    raftActor
        .call(
            () -> {
              final ArgumentMatcher<RemoteAddress> remoteAddressMatcher =
                  r -> r.getAddress().equals(other.socketAddress);
              doCallRealMethod()
                  .when(spyClientOutput)
                  .sendRequest(argThat(remoteAddressMatcher), any());
              doCallRealMethod()
                  .when(spyClientOutput)
                  .sendRequest(argThat(remoteAddressMatcher), any(), any());
              final RemoteAddress remoteAddress =
                  clientTransport.registerRemoteAddress(other.getSocketAddress());
              interrupedStreams.remove(remoteAddress.getStreamId());
            })
        .join();
  }
}
