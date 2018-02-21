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

import static io.zeebe.raft.state.RaftState.LEADER;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.zeebe.dispatcher.*;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.*;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftConfiguration;
import io.zeebe.raft.RaftStateListener;
import io.zeebe.raft.event.RaftConfigurationEvent;
import io.zeebe.raft.event.RaftConfigurationEventMember;
import io.zeebe.raft.state.RaftState;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.*;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.DirectBuffer;
import org.junit.rules.ExternalResource;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

public class RaftRule extends ExternalResource implements RaftStateListener
{

    public static final FragmentHandler NOOP_FRAGMENT_HANDLER = (buffer, offset, length, streamId, isMarkedFailed) -> FragmentHandler.CONSUME_FRAGMENT_RESULT;

    protected final ActorSchedulerRule actorSchedulerRule;
    protected final RaftConfiguration configuration;
    protected final SocketAddress socketAddress;
    protected final String topicName;
    protected final int partition;
    protected final RaftConfigurationEvent configurationEvent = new RaftConfigurationEvent();
    protected final LogStreamWriterImpl writer = new LogStreamWriterImpl();
    protected final List<RaftRule> members;
    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();

    protected ClientTransport clientTransport;
    protected ClientOutput spyClientOutput;

    protected Dispatcher clientSendBuffer;

    protected Dispatcher serverSendBuffer;
    protected BufferingServerTransport serverTransport;

    protected Dispatcher serverReceiveBuffer;

    protected LogStream logStream;
    protected Raft raft;
    protected BufferedLogStreamReader uncommittedReader;
    protected BufferedLogStreamReader committedReader;

    private InMemoryRaftPersistentStorage persistentStorage;

    protected final List<RaftState> raftStateChanges = new ArrayList<>();

    public RaftRule(final ActorSchedulerRule actorSchedulerRule, final String host, final int port, final String topicName, final int partition, final RaftRule... members)
    {
        this(actorSchedulerRule, new RaftConfiguration(), host, port, topicName, partition, members);
    }

    public RaftRule(final ActorSchedulerRule actorSchedulerRule, final RaftConfiguration configuration, final String host, final int port, final String topicName, final int partition, final RaftRule... members)
    {
        this.actorSchedulerRule = actorSchedulerRule;
        this.configuration = configuration;
        this.socketAddress = new SocketAddress(host, port);

        this.topicName = topicName;
        this.partition = partition;
        this.members = members != null ? Arrays.asList(members) : Collections.emptyList();
    }

    @Override
    protected void before() throws Throwable
    {
        final String name = socketAddress.toString();

        serverSendBuffer =
            Dispatchers.create("serverSendBuffer-" + name)
                       .bufferSize(32 * 1024 * 1024)
                       .subscriptions("sender-" + name)
                       .actorScheduler(actorSchedulerRule.get())
                       .build();

        serverReceiveBuffer =
            Dispatchers.create("serverReceiveBuffer-" + name)
                       .bufferSize(32 * 1024 * 1024)
                       .subscriptions("sender-" + name)
                       .actorScheduler(actorSchedulerRule.get())
                       .build();

        serverTransport =
            Transports.newServerTransport()
                      .sendBuffer(serverSendBuffer)
                      .bindAddress(socketAddress.toInetSocketAddress())
                      .scheduler(actorSchedulerRule.get())
                      .buildBuffering(serverReceiveBuffer);

        clientSendBuffer =
            Dispatchers.create("clientSendBuffer-" + name)
                       .bufferSize(32 * 1024 * 1024)
                       .subscriptions("sender-" + name)
                       .actorScheduler(actorSchedulerRule.get())
                       .build();

        clientTransport =
            Transports.newClientTransport()
                      .sendBuffer(clientSendBuffer)
                      .requestPoolSize(128)
                      .scheduler(actorSchedulerRule.get())
                      .build();

        logStream =
            LogStreams.createFsLogStream(wrapString(topicName), partition)
                      .deleteOnClose(true)
                      .logDirectory(Files.createTempDirectory("raft-test-" + socketAddress.port() + "-").toString())
                      .actorScheduler(actorSchedulerRule.get())
                      .build();

        logStream.open();

        persistentStorage = new InMemoryRaftPersistentStorage(logStream);

        spyClientOutput = spy(clientTransport.getOutput());
        final ClientTransport spyClientTransport = spy(clientTransport);
        when(spyClientTransport.getOutput()).thenReturn(spyClientOutput);

        raft = new Raft(configuration, socketAddress, logStream, serverTransport, spyClientTransport, persistentStorage, this)
        {
            @Override
            public String getName()
            {
                return socketAddress.toString();
            }
        };
        raft.addMembers(members.stream().map(RaftRule::getSocketAddress).collect(Collectors.toList()));

        uncommittedReader = new BufferedLogStreamReader(logStream, true);
        committedReader = new BufferedLogStreamReader(logStream, false);

        schedule();
    }

    @Override
    protected void after()
    {
        final ActorFuture<Void> close = raft.close();

        while (!close.isDone())
        {
        }

        logStream.close();

        serverTransport.close();
        serverSendBuffer.close();
        serverReceiveBuffer.close();

        clientTransport.close();
        clientSendBuffer.close();

        uncommittedReader.close();
        committedReader.close();
    }

    public void schedule()
    {
        actorSchedulerRule.get().submitActor(raft);
    }

    public SocketAddress getSocketAddress()
    {
        return socketAddress;
    }

    public LogStream getLogStream()
    {
        return logStream;
    }

    public int getTerm()
    {
        return raft.getTerm();
    }

    public Raft getRaft()
    {
        return raft;
    }

    public RaftConfiguration getConfiguration()
    {
        return raft.getConfiguration();
    }

    public RaftState getState()
    {
        return raft.getState();
    }

    @Override
    public void onStateChange(final int partitionId, DirectBuffer topicName, final SocketAddress socketAddress, final RaftState raftState)
    {
        assertThat(partitionId).isEqualTo(raft.getLogStream().getPartitionId());
        assertThat(topicName).isEqualByComparingTo(raft.getLogStream().getTopicName());
        assertThat(socketAddress).isEqualTo(raft.getSocketAddress());
        raftStateChanges.add(raftState);
    }

    public List<RaftState> getRaftStateChanges()
    {
        return raftStateChanges;
    }

    public InMemoryRaftPersistentStorage getPersistentStorage()
    {
        return persistentStorage;
    }

    public void clearSubscription()
    {
        final String subscriptionName = raft.getSubscriptionName();
        final Subscription subscription = serverReceiveBuffer.getSubscription(subscriptionName);
        subscription.poll(NOOP_FRAGMENT_HANDLER, Integer.MAX_VALUE);
    }

    public boolean isLeader()
    {
        return getState() == LEADER;
    }

    public long writeEvent(final String message)
    {

        final long[] writtenPosition = new long[1];

        writer.wrap(logStream);

        final DirectBuffer value = wrapString(message);

        TestUtil.doRepeatedly(() -> writer.positionAsKey().metadataWriter(metadata.reset()).value(value).tryWrite())
                .until(position ->
                {
                    if (position != null && position >= 0)
                    {
                        writtenPosition[0] = position;
                        return true;
                    }
                    else
                    {
                        return false;
                    }
                }, "Failed to write event with message {}", message);

        return writtenPosition[0];
    }

    public long writeEvents(final String... messages)
    {
        long position = 0;

        for (final String message : messages)
        {
            position = writeEvent(message);
        }

        return position;
    }

    public boolean eventAppended(final long position, final int term, final String message)
    {
        uncommittedReader.seek(position);

        if (uncommittedReader.hasNext())
        {
            final LoggedEvent event = uncommittedReader.next();
            final String value = bufferAsString(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());

            return event.getPosition() == position && event.getRaftTerm() == term && message.equals(value);
        }

        return false;
    }

    public boolean eventCommitted(final long position, final int term, final String message)
    {
        final boolean isCommitted = logStream.getCommitPosition() >= position;

        return isCommitted && eventAppended(position, term, message);
    }

    public boolean eventsCommitted(final String... messages)
    {
        committedReader.seekToFirstEvent();

        final BrokerEventMetadata metadata = new BrokerEventMetadata();

        return
            Arrays.stream(messages)
                  .allMatch(message ->
                  {
                      while (committedReader.hasNext())
                      {
                          final LoggedEvent event = committedReader.next();
                          event.readMetadata(metadata);

                          if (metadata.getEventType() == EventType.NULL_VAL)
                          {
                              try
                              {
                                  final String value = bufferAsString(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());

                                  return message.equals(value);
                              }
                              catch (final Exception e)
                              {
                                  // ignore
                              }
                          }
                      }

                      return false;
                  });
    }

    public boolean eventCommitted(final int term, final EventType eventType)
    {
        committedReader.seekToFirstEvent();

        while (committedReader.hasNext())
        {
            final LoggedEvent event = committedReader.next();
            event.readMetadata(metadata);

            if (event.getRaftTerm() == term && metadata.getEventType() == eventType)
            {
                return true;
            }
            else if (event.getRaftTerm() > term)
            {
                // early abort which assumes that terms are always increasing
                return false;
            }
        }

        return false;
    }

    public boolean raftEventCommitted(final int term, final RaftRule... members)
    {
        committedReader.seekToFirstEvent();

        final Set<SocketAddress> expected;

        if (members == null)
        {
            expected = Collections.emptySet();
        }
        else
        {
            expected = Arrays.stream(members).map(RaftRule::getSocketAddress).collect(Collectors.toSet());
        }


        while (committedReader.hasNext())
        {
            final LoggedEvent event = committedReader.next();
            event.readMetadata(metadata);

            if (event.getRaftTerm() == term && metadata.getEventType() == EventType.RAFT_EVENT)
            {
                event.readValue(configurationEvent);

                final Iterator<RaftConfigurationEventMember> configurationMembers = configurationEvent.members().iterator();

                final Set<SocketAddress> found = new HashSet<>();

                while (configurationMembers.hasNext())
                {
                    final RaftConfigurationEventMember configurationMember = configurationMembers.next();
                    found.add(configurationMember.getSocketAddress());
                }

                if (expected.equals(found))
                {
                    return true;
                }
            }
            else if (event.getRaftTerm() > term)
            {
                // early abort which assumes that terms are always increasing
                return false;
            }
        }

        return false;
    }

    @Override
    public String toString()
    {
        return raft.toString();
    }


    public void interruptConnectionTo(RaftRule other)
    {
        final ClientRequest clientRequest = mock(ClientRequest.class);

        final ArgumentMatcher<RemoteAddress> remoteAddressMatcher = r -> other.socketAddress.equals(r.getAddress());
        doReturn(clientRequest)
            .when(spyClientOutput)
            .sendRequest(argThat(remoteAddressMatcher), any());

        doReturn(CompletableActorFuture.completedExceptionally(new RuntimeException("connection is interrupted")))
            .when(spyClientOutput)
            .sendRequestWithRetry(argThat(remoteAddressMatcher), any());

        doReturn(CompletableActorFuture.completedExceptionally(new TimeoutException("timeout to " + other.socketAddress)))
            .when(spyClientOutput)
            .sendRequestWithRetry(argThat(remoteAddressMatcher), any(), any());

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(other.getSocketAddress());
        Mockito.doReturn(false)
            .when(spyClientOutput)
            .sendMessage(argThat(transportMessage -> readRemoteStreamId(transportMessage) == remoteAddress.getStreamId()));
    }

    private int readRemoteStreamId(TransportMessage transportMessage)
    {
        final Class<TransportMessage> transportMessageClass = TransportMessage.class;
        int value;
        try
        {
            final Field remoteStreamId = transportMessageClass.getDeclaredField("remoteStreamId");
            remoteStreamId.setAccessible(true);
            value = (int) remoteStreamId.get(transportMessage);
        }
        catch (Exception e)
        {
            value = -1;
        }
        return value;
    }

    public void reconnectTo(RaftRule other)
    {
        final ArgumentMatcher<RemoteAddress> remoteAddressMatcher = r -> r.getAddress().equals(other.socketAddress);
        doCallRealMethod().when(spyClientOutput).sendRequest(argThat(r -> r.getAddress().equals(other.socketAddress)), any());
        doCallRealMethod().when(spyClientOutput).sendRequestWithRetry(argThat(remoteAddressMatcher), any());
        doCallRealMethod().when(spyClientOutput).sendRequestWithRetry(argThat(remoteAddressMatcher), any(), any());
        doCallRealMethod().when(spyClientOutput).sendMessage(any());
    }
}
