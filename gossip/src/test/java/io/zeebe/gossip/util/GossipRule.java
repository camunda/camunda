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
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
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
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.RequestResponseHeaderDescriptor;
import io.zeebe.transport.impl.TransportHeaderDescriptor;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.ByteValue;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.junit.rules.ExternalResource;

public class GossipRule extends ExternalResource {

  private final int nodeId;
  private final SocketAddress socketAddress;

  private ActorScheduler actorScheduler;
  private GossipConfiguration configuration;
  private Gossip gossip;

  private ClientTransport clientTransport;

  private BufferingServerTransport serverTransport;
  private Dispatcher serverReceiveBuffer;

  private LocalMembershipListener localMembershipListener;
  private ReceivedEventsCollector receivedEventsCollector = new ReceivedEventsCollector();

  public GossipRule(final int nodeId) {
    this.nodeId = nodeId;
    this.socketAddress = SocketUtil.getNextAddress();
  }

  public void init(ActorScheduler actorScheduler, GossipConfiguration configuration) {
    this.actorScheduler = actorScheduler;
    this.configuration = configuration;
  }

  @Override
  protected void before() throws Throwable {
    assertThat(actorScheduler).isNotNull();

    final String name = socketAddress.toString();

    serverReceiveBuffer =
        Dispatchers.create("serverReceiveBuffer-" + name)
            .bufferSize(ByteValue.ofMegabytes(32))
            .subscriptions("sender" + name)
            .actorScheduler(actorScheduler)
            .build();

    serverTransport =
        Transports.newServerTransport()
            .bindAddress(socketAddress.toInetSocketAddress())
            .scheduler(actorScheduler)
            .buildBuffering(serverReceiveBuffer);

    clientTransport =
        Transports.newClientTransport(name)
            .scheduler(actorScheduler)
            .inputListener(receivedEventsCollector)
            .build();

    // We need to create and submit gossip in an actor because ActorClock is only
    // accessible inside an actor.
    actorScheduler
        .submitActor(
            new Actor() {
              @Override
              protected void onActorStarting() {
                gossip = new Gossip(nodeId, serverTransport, clientTransport, configuration);

                // we need to use runOnCompletion because #join is not allowed inside an actor -
                // will block which is forbiddem
                actor.runOnCompletion(
                    actorScheduler.submitActor(gossip),
                    (v, t) -> {
                      localMembershipListener = new LocalMembershipListener();
                      gossip.addMembershipListener(localMembershipListener);
                    });
              }
            })
        .join();

    actorScheduler
        .submitActor(
            new Actor() {
              @Override
              protected void onActorStarting() {
                final ActorFuture<Subscription> future =
                    serverReceiveBuffer.openSubscriptionAsync("received-events-collector");
                actor.runOnCompletion(
                    future,
                    (sub, t) ->
                        actor.consume(
                            sub, () -> sub.poll(receivedEventsCollector, Integer.MAX_VALUE)));
              }
            })
        .join();
  }

  @Override
  protected void after() {
    gossip.close().join();

    serverTransport.close();
    clientTransport.close();
    serverReceiveBuffer.close();
  }

  public ActorFuture<Void> join(GossipRule... contactPoints) {
    final List<SocketAddress> contactPointList =
        Arrays.stream(contactPoints).map(c -> c.socketAddress).collect(toList());

    return getController().join(contactPointList);
  }

  public ActorFuture<Void> leave() {
    return getController().leave();
  }

  public void interruptConnectionTo(GossipRule other) {
    clientTransport.deactivateEndpoint(other.getNodeId());
    other.clientTransport.deactivateEndpoint(this.getNodeId());
  }

  public void reconnectTo(GossipRule other) {
    clientTransport.registerEndpoint(other.getNodeId(), other.socketAddress);
    other.clientTransport.registerEndpoint(this.getNodeId(), this.socketAddress);
  }

  public GossipController getController() {
    return gossip;
  }

  public GossipEventPublisher getPublisher() {
    return gossip;
  }

  public int getNodeId() {
    return nodeId;
  }

  public boolean receivedEvent(GossipEventType eventType, GossipRule sender) {
    return getReceivedGossipEvents(eventType, sender).findFirst().isPresent();
  }

  public boolean receivedMembershipEvent(MembershipEventType eventType, GossipRule member) {
    return getReceivedMembershipEvents(eventType, member).findFirst().isPresent();
  }

  public boolean receivedCustomEvent(DirectBuffer eventType, GossipRule sender) {
    return getReceivedCustomEvents(eventType, sender).findFirst().isPresent();
  }

  public Stream<GossipEvent> getReceivedGossipEvents(GossipEventType eventType, GossipRule sender) {
    return receivedEventsCollector
        .gossipEvents()
        .filter(e -> e.getEventType() == eventType)
        .filter(e -> e.getSenderId() == sender.nodeId);
  }

  public Stream<MembershipEvent> getReceivedMembershipEvents(
      MembershipEventType eventType, GossipRule member) {
    return receivedEventsCollector
        .membershipEvents()
        .filter(e -> e.getType() == eventType)
        .filter(e -> e.getMemberId() == member.nodeId);
  }

  public Stream<CustomEvent> getReceivedCustomEvents(DirectBuffer eventType, GossipRule sender) {
    return receivedEventsCollector
        .customEvents()
        .filter(e -> BufferUtil.equals(eventType, e.getType()))
        .filter(e -> e.getSenderId() == sender.nodeId);
  }

  public void clearReceivedEvents() {
    receivedEventsCollector.clear();
  }

  public boolean hasMember(GossipRule member) {
    return localMembershipListener.hasMember(member.nodeId);
  }

  public Member getMember(GossipRule member) {
    return localMembershipListener.getMember(member.nodeId);
  }

  private static class ReceivedEventsCollector implements ClientInputListener, FragmentHandler {
    private class ReceivedEvent {
      private final List<MembershipEvent> membershipEvents = new ArrayList<>();
      private final List<CustomEvent> customEvents = new ArrayList<>();

      private final GossipEvent gossipEvent =
          new GossipEvent(
              null,
              event -> {
                final MembershipEvent membershipEvent =
                    new MembershipEvent()
                        .type(event.getType())
                        .gossipTerm(event.getGossipTerm())
                        .memberId(event.getMemberId());

                membershipEvents.add(membershipEvent);

                return true;
              },
              null,
              event -> {
                final CustomEvent customEvent =
                    new CustomEvent()
                        .senderGossipTerm(event.getSenderGossipTerm())
                        .senderId(event.getSenderId())
                        .type(event.getType())
                        .payload(event.getPayload());

                customEvents.add(customEvent);

                return true;
              },
              0,
              0);

      ReceivedEvent(DirectBuffer buffer, int offset, int length) {
        gossipEvent.wrap(buffer, offset, length);
      }
    }

    private final List<ReceivedEvent> receivedEvents = new CopyOnWriteArrayList<>();

    @Override
    public void onResponse(
        int streamId, long requestId, DirectBuffer buffer, int offset, int length) {
      final ReceivedEvent event = new ReceivedEvent(buffer, offset, length);
      receivedEvents.add(event);
    }

    @Override
    public void onMessage(int streamId, DirectBuffer buffer, int offset, int length) {
      // currently, we send no messages
    }

    @Override
    public int onFragment(
        DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed) {
      final int headerLengths =
          TransportHeaderDescriptor.headerLength() + RequestResponseHeaderDescriptor.headerLength();

      final ReceivedEvent event =
          new ReceivedEvent(buffer, offset + headerLengths, length - headerLengths);
      receivedEvents.add(event);

      return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

    public void clear() {
      receivedEvents.clear();
    }

    public Stream<GossipEvent> gossipEvents() {
      return receivedEvents.stream().map(e -> e.gossipEvent);
    }

    public Stream<MembershipEvent> membershipEvents() {
      return receivedEvents.stream().flatMap(e -> e.membershipEvents.stream());
    }

    public Stream<CustomEvent> customEvents() {
      return receivedEvents.stream().flatMap(e -> e.customEvents.stream());
    }
  }

  private static class LocalMembershipListener implements GossipMembershipListener {
    private final Map<Integer, Member> members = new ConcurrentHashMap<>();

    @Override
    public void onAdd(Member member) {
      members.put(member.getId(), member);
    }

    @Override
    public void onRemove(Member member) {
      members.remove(member.getId());
    }

    public boolean hasMember(int nodeId) {
      return members.containsKey(nodeId);
    }

    public Member getMember(int nodeId) {
      return members.get(nodeId);
    }
  }

  public static final class ReceivedCustomEvent {
    private final int senderId;
    private final DirectBuffer payload;

    public ReceivedCustomEvent(int senderId, DirectBuffer payload) {
      this.senderId = senderId;
      this.payload = BufferUtil.cloneBuffer(payload);
    }

    public int getSenderId() {
      return senderId;
    }

    public DirectBuffer getPayload() {
      return payload;
    }
  }

  public static final class RecordingCustomEventListener implements GossipCustomEventListener {
    private final List<ReceivedCustomEvent> receivedEvents = new CopyOnWriteArrayList<>();

    @Override
    public void onEvent(int nodeId, DirectBuffer payload) {
      final ReceivedCustomEvent event = new ReceivedCustomEvent(nodeId, payload);

      receivedEvents.add(event);
    }

    public Stream<ReceivedCustomEvent> getInvocations() {
      return receivedEvents.stream();
    }
  }
}
