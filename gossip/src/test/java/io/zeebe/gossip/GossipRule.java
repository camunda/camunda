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
package io.zeebe.gossip;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.gossip.membership.Member;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.*;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;
import org.junit.rules.ExternalResource;
import org.mockito.ArgumentMatcher;

public class GossipRule extends ExternalResource
{

    private final Supplier<ActorScheduler> actionSchedulerSupplier;
    private final GossipConfiguration configuration;
    private final SocketAddress socketAddress;

    private Gossip gossip;

    private ClientTransport clientTransport;
    private Dispatcher clientSendBuffer;

    private BufferingServerTransport serverTransport;
    private Dispatcher serverSendBuffer;
    private Dispatcher serverReceiveBuffer;

    private ActorReference actorReference;

    private RecordingMembershipListener recordingMembershipListener;

    public GossipRule(final Supplier<ActorScheduler> actionSchedulerSupplier, final GossipConfiguration configuration, final String host, final int port)
    {
        this.actionSchedulerSupplier = actionSchedulerSupplier;
        this.configuration = configuration;
        this.socketAddress = new SocketAddress(host, port);
    }

    @Override
    protected void before() throws Throwable
    {
        final String name = socketAddress.toString();

        final ActorScheduler actorScheduler = actionSchedulerSupplier.get();

        serverSendBuffer = Dispatchers
                .create("serverSendBuffer-" + name)
                .bufferSize(32 * 1024 * 1024)
                .subscriptions("sender")
                .actorScheduler(actorScheduler)
                .build();

        serverReceiveBuffer = Dispatchers
                .create("serverReceiveBuffer-" + name)
                .bufferSize(32 * 1024 * 1024)
                .subscriptions("sender")
                .actorScheduler(actorScheduler)
                .build();

        serverTransport = Transports
                .newServerTransport()
                .sendBuffer(serverSendBuffer)
                .bindAddress(socketAddress.toInetSocketAddress())
                .scheduler(actorScheduler)
                .buildBuffering(serverReceiveBuffer);

        clientSendBuffer = Dispatchers
                .create("clientSendBuffer-" + name)
                .bufferSize(32 * 1024 * 1024)
                .subscriptions("sender")
                .actorScheduler(actorScheduler)
                .build();

        clientTransport = Transports
                .newClientTransport()
                .sendBuffer(clientSendBuffer)
                .requestPoolSize(128)
                .scheduler(actorScheduler)
                .build();

        // TODO make it more safe
        clientTransport = spy(clientTransport);

        final ClientOutput clientOutput = spy(clientTransport.getOutput());

        when(clientTransport.getOutput()).thenReturn(clientOutput);

        gossip = new Gossip(socketAddress, serverTransport, clientTransport, configuration);

        actorReference = actorScheduler.schedule(gossip);

        recordingMembershipListener = new RecordingMembershipListener();
        gossip.addMembershipListener(recordingMembershipListener);
    }

    @Override
    protected void after()
    {
        serverTransport.close();
        serverSendBuffer.close();
        serverReceiveBuffer.close();

        clientTransport.close();
        clientSendBuffer.close();

        actorReference.close();
    }

    public void join(String host, int port)
    {
        final SocketAddress address = new SocketAddress(host, port);

        getController().join(Collections.singletonList(address));
    }

    public void interruptConnection(String host, int port)
    {
        final ClientRequest clientRequest = mock(ClientRequest.class);

        final ClientOutput clientOutput = clientTransport.getOutput();
        doReturn(clientRequest).when(clientOutput).sendRequest(argThat(matches(host, port)), any());
    }

    public void reconnect(String host, int port)
    {
        final ClientOutput clientOutput = clientTransport.getOutput();
        doCallRealMethod().when(clientOutput).sendRequest(argThat(matches(host, port)), any());
    }

    private ArgumentMatcher<RemoteAddress> matches(String host, int port)
    {
        return remoteAddr ->
        {
            final SocketAddress address = remoteAddr.getAddress();
            return address.host().equals(host) && address.port() == port;
        };
    }

    public GossipController getController()
    {
        return gossip;
    }

    public void awaitAdded(String host, int port)
    {
        TestUtil.waitUntil(() -> recordingMembershipListener.events.stream()
                           .filter(RecordingMembershipListener.withHostAndPort(host, port))
                           .filter(RecordingMembershipListener.add())
                           .findFirst()
                           .isPresent());
    }

    public void awaitRemoved(String host, int port)
    {
        TestUtil.waitUntil(() -> recordingMembershipListener.events.stream()
                           .filter(RecordingMembershipListener.withHostAndPort(host, port))
                           .filter(RecordingMembershipListener.remove())
                           .findFirst()
                           .isPresent());
    }

    private static class RecordingMembershipListener implements GossipMembershipListener
    {
        private List<RecordedMembershipEvent> events = new ArrayList<>();

        enum RecordedMembershipEventType
        {
            ADD, REMOVE
        }

        class RecordedMembershipEvent
        {
            private Member member;
            private RecordedMembershipEventType type;
        }

        public static Predicate<RecordedMembershipEvent> withHostAndPort(String host, int port)
        {
            return e ->
            {
                final SocketAddress addr = e.member.getAddress();

                return addr.host().equals(host) && addr.port() == port;
            };
        }

        public static Predicate<RecordedMembershipEvent> add()
        {
            return e -> e.type == RecordedMembershipEventType.ADD;
        }

        public static Predicate<RecordedMembershipEvent> remove()
        {
            return e -> e.type == RecordedMembershipEventType.REMOVE;
        }

        @Override
        public void onAdd(Member member)
        {
            final RecordedMembershipEvent event = new RecordedMembershipEvent();
            event.member = member;
            event.type = RecordedMembershipEventType.ADD;

            events.add(event);
        }

        @Override
        public void onRemove(Member member)
        {
            final RecordedMembershipEvent event = new RecordedMembershipEvent();
            event.member = member;
            event.type = RecordedMembershipEventType.REMOVE;

            events.add(event);
        }
    }

}
