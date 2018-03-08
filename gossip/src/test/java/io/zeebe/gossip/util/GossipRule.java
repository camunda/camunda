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
package io.zeebe.gossip.util;

import static java.util.stream.Collectors.toList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.gossip.Gossip;
import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.gossip.GossipController;
import io.zeebe.gossip.GossipCustomEventListener;
import io.zeebe.gossip.GossipEventPublisher;
import io.zeebe.gossip.GossipMembershipListener;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.protocol.CustomEvent;
import io.zeebe.gossip.protocol.GossipEvent;
import io.zeebe.gossip.protocol.MembershipEvent;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientInputListener;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.RequestResponseHeaderDescriptor;
import io.zeebe.transport.impl.TransportHeaderDescriptor;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;
import org.junit.rules.ExternalResource;
import org.mockito.ArgumentMatcher;

public class GossipRule extends ExternalResource
{

    private final Supplier<ActorScheduler> actionSchedulerSupplier;
    private final GossipConfiguration configuration;
    private final SocketAddress socketAddress;
    private final String memberId;

    private Gossip gossip;
    private ActorControl gossipActor;

    private ClientTransport clientTransport;
    private Dispatcher clientSendBuffer;

    private BufferingServerTransport serverTransport;
    private Dispatcher serverSendBuffer;
    private Dispatcher serverReceiveBuffer;

    private ClientOutput spyClientOutput;

    private LocalMembershipListener localMembershipListener;
    private ReceivedEventsCollector receivedEventsCollector = new ReceivedEventsCollector();

    public GossipRule(final Supplier<ActorScheduler> actionSchedulerSupplier, final GossipConfiguration configuration, final String host, final int port)
    {
        this.actionSchedulerSupplier = actionSchedulerSupplier;
        this.configuration = configuration;
        this.socketAddress = new SocketAddress(host, port);
        this.memberId = String.format("%s:%d", host, port);
    }

    @Override
    protected void before() throws Throwable
    {
        receivedEventsCollector.clear();

        final String name = socketAddress.toString();

        final ActorScheduler actorScheduler = actionSchedulerSupplier.get();

        serverSendBuffer = Dispatchers
                .create("serverSendBuffer-" + name)
                .bufferSize(32 * 1024 * 1024)
                .subscriptions("sender" + name)
                .actorScheduler(actorScheduler)
                .build();

        serverReceiveBuffer = Dispatchers
                .create("serverReceiveBuffer-" + name)
                .bufferSize(32 * 1024 * 1024)
                .subscriptions("sender" + name)
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
                .subscriptions("sender" + name)
                .actorScheduler(actorScheduler)
                .build();

        clientTransport = Transports
                .newClientTransport()
                .sendBuffer(clientSendBuffer)
                .requestPoolSize(128)
                .scheduler(actorScheduler)
                .inputListener(receivedEventsCollector)
                .build();


        spyClientOutput = spy(clientTransport.getOutput());

        final ClientTransport spyClientTransport = spy(clientTransport);
        when(spyClientTransport.getOutput()).thenReturn(spyClientOutput);


        gossip = new Gossip(socketAddress, serverTransport, spyClientTransport, configuration)
        {
            @Override
            protected void onActorStarting()
            {
                gossipActor = actor;
            }

            @Override
            public String getName()
            {
                // make it easier to distinguish different gossip runners
                return socketAddress.toString();
            }
        };
        actorScheduler.submitActor(gossip).join();

        localMembershipListener = new LocalMembershipListener();
        gossip.addMembershipListener(localMembershipListener);

        actorScheduler.submitActor(new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.await(serverReceiveBuffer.openSubscriptionAsync("received-events-collector"), (sub, t) ->
                {
                    actor.consume(sub, () -> sub.poll(receivedEventsCollector, Integer.MAX_VALUE));
                });
            }
        });
    }

    @Override
    protected void after()
    {
        gossip.close().join();

        serverTransport.close();
        clientTransport.close();

        serverSendBuffer.close();
        serverReceiveBuffer.close();

        clientSendBuffer.close();
    }

    public ActorFuture<Void> join(GossipRule... contactPoints)
    {
        final List<SocketAddress> contactPointList = Arrays.stream(contactPoints).map(c -> c.socketAddress).collect(toList());

        return getController().join(contactPointList);
    }

    public ActorFuture<Void> leave()
    {
        return getController().leave();
    }

    public void interruptConnectionTo(GossipRule other)
    {
        // HINT we need to do this as actor.call since we need to sync with the gossip actor thread
        gossipActor.call(() ->
        {
            final ClientRequest clientRequest = mock(ClientRequest.class);

            final ArgumentMatcher<RemoteAddress> remoteAddressMatcher = r -> other.socketAddress.equals(r.getAddress());
            doReturn(clientRequest)
                .when(spyClientOutput)
                .sendRequest(argThat(remoteAddressMatcher), any());

            doReturn(CompletableActorFuture.completedExceptionally(new RuntimeException("connection is interrupted")))
                .when(spyClientOutput)
                .sendRequestWithRetry(argThat(remoteAddressMatcher), any());

            doReturn(CompletableActorFuture.completedExceptionally(new TimeoutException("timeout")))
                .when(spyClientOutput)
                .sendRequestWithRetry(argThat(remoteAddressMatcher), any(), any());

        });
    }

    public void reconnectTo(GossipRule other)
    {
        // HINT we need to do this as actor.call since we need to sync with the gossip actor thread
        gossipActor.call(() ->
        {
            final ArgumentMatcher<RemoteAddress> remoteAddressMatcher = r -> r.getAddress().equals(other.socketAddress);
            doCallRealMethod().when(spyClientOutput).sendRequest(argThat(r -> r.getAddress().equals(other.socketAddress)), any());
            doCallRealMethod().when(spyClientOutput).sendRequestWithRetry(argThat(remoteAddressMatcher), any());
            doCallRealMethod().when(spyClientOutput).sendRequestWithRetry(argThat(remoteAddressMatcher), any(), any());
        });
    }

    public GossipController getController()
    {
        return gossip;
    }

    public GossipEventPublisher getPushlisher()
    {
        return gossip;
    }

    public SocketAddress getAddress()
    {
        return socketAddress;
    }

    public boolean receivedEvent(GossipEventType eventType, GossipRule sender)
    {
        return receivedEventsCollector.gossipEvents()
                .filter(e -> e.getEventType() == eventType)
                .anyMatch(e -> e.getSender().equals(sender.socketAddress));
    }

    public boolean receivedMembershipEvent(MembershipEventType eventType, GossipRule member)
    {
        return getReceivedMembershipEvents(eventType, member)
                .findFirst()
                .isPresent();
    }

    public boolean receivedCustomEvent(DirectBuffer eventType, GossipRule sender)
    {
        return getReceivedCustomEvents(eventType, sender)
                .findFirst()
                .isPresent();
    }

    public Stream<MembershipEvent> getReceivedMembershipEvents(MembershipEventType eventType, GossipRule member)
    {
        return receivedEventsCollector.membershipEvents()
                .filter(e -> e.getType() == eventType)
                .filter(e -> e.getAddress().equals(member.socketAddress));
    }

    public Stream<CustomEvent> getReceivedCustomEvents(DirectBuffer eventType, GossipRule sender)
    {
        return receivedEventsCollector.customEvents()
                .filter(e -> BufferUtil.equals(eventType, e.getType()))
                .filter(e -> e.getSenderAddress().equals(sender.socketAddress));
    }

    public void clearReceivedEvents()
    {
        receivedEventsCollector.clear();
    }

    public boolean hasMember(GossipRule member)
    {
        return localMembershipListener.hasMember(member.memberId);
    }

    public Member getMember(GossipRule member)
    {
        return localMembershipListener.getMember(member.memberId);
    }

    private static class ReceivedEventsCollector implements ClientInputListener, FragmentHandler
    {
        private class ReceivedEvent
        {
            private final List<MembershipEvent> membershipEvents = new ArrayList<>();
            private final List<CustomEvent> customEvents = new ArrayList<>();

            private final GossipEvent gossipEvent = new GossipEvent(null, event ->
            {
                final MembershipEvent membershipEvent = new MembershipEvent()
                        .type(event.getType())
                        .gossipTerm(event.getGossipTerm())
                        .address(event.getAddress());

                membershipEvents.add(membershipEvent);

                return true;
            }, null, event ->
            {
                final CustomEvent customEvent = new CustomEvent()
                        .senderGossipTerm(event.getSenderGossipTerm())
                        .senderAddress(event.getSenderAddress())
                        .type(event.getType())
                        .payload(event.getPayload());

                customEvents.add(customEvent);

                return true;
            }, 0, 0);

            ReceivedEvent(DirectBuffer buffer, int offset, int length)
            {
                gossipEvent.wrap(buffer, offset, length);
            }
        }

        private final List<ReceivedEvent> receivedEvents = new CopyOnWriteArrayList<>();

        @Override
        public void onResponse(int streamId, long requestId, DirectBuffer buffer, int offset, int length)
        {
            final ReceivedEvent event = new ReceivedEvent(buffer, offset, length);
            receivedEvents.add(event);
        }

        @Override
        public void onMessage(int streamId, DirectBuffer buffer, int offset, int length)
        {
            // currently, we send no messages
        }

        @Override
        public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
        {
            final int headerLengths = TransportHeaderDescriptor.headerLength() + RequestResponseHeaderDescriptor.headerLength();

            final ReceivedEvent event = new ReceivedEvent(buffer, offset + headerLengths, length - headerLengths);
            receivedEvents.add(event);

            return FragmentHandler.CONSUME_FRAGMENT_RESULT;
        }

        public void clear()
        {
            receivedEvents.clear();
        }

        public Stream<GossipEvent> gossipEvents()
        {
            return receivedEvents.stream().map(e -> e.gossipEvent);
        }

        public Stream<MembershipEvent> membershipEvents()
        {
            return receivedEvents.stream().flatMap(e -> e.membershipEvents.stream());
        }

        public Stream<CustomEvent> customEvents()
        {
            return receivedEvents.stream().flatMap(e -> e.customEvents.stream());
        }
    }

    private static class LocalMembershipListener implements GossipMembershipListener
    {
        private final Map<String, Member> members = new HashMap<>();

        @Override
        public void onAdd(Member member)
        {
            members.put(member.getId(), member);
        }

        @Override
        public void onRemove(Member member)
        {
            members.remove(member.getId());
        }

        public boolean hasMember(String id)
        {
            return members.containsKey(id);
        }

        public Member getMember(String id)
        {
            return members.get(id);
        }
    }

    public static final class ReceivedCustomEvent
    {
        private final SocketAddress sender;
        private final DirectBuffer payload;

        public ReceivedCustomEvent(SocketAddress sender, DirectBuffer payload)
        {
            this.sender = new SocketAddress(sender);
            this.payload = BufferUtil.cloneBuffer(payload);
        }

        public SocketAddress getSender()
        {
            return sender;
        }

        public DirectBuffer getPayload()
        {
            return payload;
        }
    }

    public static final class RecordingCustomEventListener implements GossipCustomEventListener
    {
        private final List<ReceivedCustomEvent> receivedEvents = new ArrayList<>();

        @Override
        public void onEvent(SocketAddress sender, DirectBuffer payload)
        {
            final ReceivedCustomEvent event = new ReceivedCustomEvent(sender, payload);

            receivedEvents.add(event);
        }

        public Stream<ReceivedCustomEvent> getInvocations()
        {
            return receivedEvents.stream();
        }
    }

}
