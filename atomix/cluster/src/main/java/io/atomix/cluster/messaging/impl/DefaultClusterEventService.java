/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster.messaging.impl;

import static io.atomix.utils.concurrent.Threads.namedThreads;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.ManagedClusterEventService;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.messaging.Subscription;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Namespace.Builder;
import io.atomix.utils.serializer.Namespaces;
import io.atomix.utils.serializer.Serializer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cluster event service. */
public class DefaultClusterEventService
    implements ManagedClusterEventService, ClusterMembershipEventListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClusterEventService.class);

  private static final Serializer SERIALIZER =
      Serializer.using(
          new Builder()
              .register(Namespaces.BASIC)
              .register(MemberId.class)
              .register(Void.class) // placeholder for the deleted LogicalTimestamp class
              .register(Void.class) // placeholder for the deleted WallClockTimestamp class
              .build());

  private static final String SUBSCRIPTION_PROPERTY_NAME = "event-service-topics-subscribed";
  private final ClusterMembershipService membershipService;
  private final MessagingService messagingService;
  private final MemberId localMemberId;
  private final Map<String, InternalTopic> topics = Maps.newConcurrentMap();
  private final Map<MemberId, Set<String>> remoteMemberSubscriptions = Maps.newConcurrentMap();
  private final AtomicBoolean started = new AtomicBoolean();
  private ScheduledExecutorService eventServiceExecutor;

  public DefaultClusterEventService(
      final ClusterMembershipService membershipService, final MessagingService messagingService) {
    this.membershipService = membershipService;
    this.messagingService = messagingService;
    localMemberId = membershipService.getLocalMember().id();
  }

  @Override
  public <M> void broadcast(
      final String topic, final M message, final Function<M, byte[]> encoder) {
    final byte[] payload = encoder.apply(message);
    getSubscriberNodes(topic)
        .forEach(
            memberId -> {
              final Member member = membershipService.getMember(memberId);
              if (member != null && member.isReachable()) {
                messagingService.sendAsync(member.address(), topic, payload);
              }
            });
  }

  @Override
  public <M, R> CompletableFuture<Subscription> subscribe(
      final String topic,
      final Function<byte[], M> decoder,
      final Function<M, R> handler,
      final Function<R, byte[]> encoder,
      final Executor executor) {
    return topics
        .computeIfAbsent(topic, t -> new InternalTopic(topic))
        .subscribe(decoder, handler, encoder, executor);
  }

  @Override
  public <M, R> CompletableFuture<Subscription> subscribe(
      final String topic,
      final Function<byte[], M> decoder,
      final Function<M, CompletableFuture<R>> handler,
      final Function<R, byte[]> encoder) {
    return topics
        .computeIfAbsent(topic, t -> new InternalTopic(topic))
        .subscribe(decoder, handler, encoder);
  }

  @Override
  public <M> CompletableFuture<Subscription> subscribe(
      final String topic,
      final Function<byte[], M> decoder,
      final Consumer<M> handler,
      final Executor executor) {
    return topics
        .computeIfAbsent(topic, t -> new InternalTopic(topic))
        .subscribe(decoder, handler, executor);
  }

  @Override
  public List<Subscription> getSubscriptions(final String topicName) {
    final InternalTopic topic = topics.get(topicName);
    if (topic == null) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(topic.localSubscriber().subscriptions());
  }

  @Override
  public Set<MemberId> getSubscribers(final String topicName) {
    final InternalTopic topic = topics.get(topicName);
    if (topic == null) {
      return Set.of();
    }
    return topic.remoteSubscriptions();
  }

  /**
   * Returns a collection of nodes that subscribe to the given topic.
   *
   * @param topicName the topic for which to return the collection of subscriber nodes
   * @return the collection of subscribers for the given topic
   */
  private Stream<MemberId> getSubscriberNodes(final String topicName) {
    return getSubscribers(topicName).stream();
  }

  /** Updates all active peers with a given subscription. */
  private CompletableFuture<Void> updateNodes() {
    final String topicSubscribed = topicsAsString(new HashSet<>(topics.keySet()));
    membershipService
        .getLocalMember()
        .properties()
        .setProperty(SUBSCRIPTION_PROPERTY_NAME, topicSubscribed);
    return CompletableFuture.completedFuture(null);
  }

  private String topicsAsString(final Set<String> topics) {
    final byte[] bytes = SERIALIZER.encode(topics);
    return new String(Base64.getEncoder().encode(bytes), StandardCharsets.UTF_8);
  }

  private Set<String> topicsFromString(final String topicsAsString) {
    final byte[] bytes =
        Base64.getDecoder().decode(topicsAsString.getBytes(StandardCharsets.UTF_8));
    return SERIALIZER.decode(bytes);
  }

  @Override
  public CompletableFuture<ClusterEventService> start() {
    if (started.compareAndSet(false, true)) {
      eventServiceExecutor =
          Executors.newSingleThreadScheduledExecutor(
              namedThreads("atomix-cluster-event-executor-%d", LOGGER));
      membershipService.addListener(this);
      // Listener doesn't receive notification about the Members added before the listener is added.
      membershipService
          .getMembers()
          .forEach(m -> event(new ClusterMembershipEvent(Type.MEMBER_ADDED, m)));
      LOGGER.info("Started");
    }
    return CompletableFuture.completedFuture(this);
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public CompletableFuture<Void> stop() {
    if (started.compareAndSet(true, false)) {
      if (eventServiceExecutor != null) {
        eventServiceExecutor.shutdown();
      }
      LOGGER.info("Stopped");
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    eventServiceExecutor.execute(
        () -> {
          switch (event.type()) {
            case MEMBER_ADDED:
              updateRemoteSubscription(event);
              break;
            case METADATA_CHANGED:
              updateRemoteSubscription(event);
              break;
            case REACHABILITY_CHANGED:
              break;
            case MEMBER_REMOVED:
              removeAllSubscription(event.subject().id());
              break;
            default:
              LOGGER.warn(
                  "Unexpected membership event type {} from {}", event.type(), event.subject());
          }
        });
  }

  private void removeAllSubscription(final MemberId id) {
    final Set<String> prevSubscriptions = remoteMemberSubscriptions.remove(id);
    if (prevSubscriptions != null) {
      prevSubscriptions.forEach(s -> topics.get(s).removeRemoteSubscription(id));
    }
  }

  private void updateRemoteSubscription(final ClusterMembershipEvent event) {
    final String topicSubscribedAsString =
        event.subject().properties().getProperty(SUBSCRIPTION_PROPERTY_NAME);
    if (topicSubscribedAsString != null) {
      final Set<String> topicsSubscribed = topicsFromString(topicSubscribedAsString);
      topicsSubscribed.forEach(
          topic ->
              topics
                  .computeIfAbsent(topic, t -> new InternalTopic(topic))
                  .addRemoteSubscription(event.subject().id()));
      remoteMemberSubscriptions.put(event.subject().id(), topicsSubscribed);
    } else {
      removeAllSubscription(event.subject().id());
    }
  }

  /** Internal subscriber. */
  private static class InternalSubscriber
      implements BiFunction<Address, byte[], CompletableFuture<byte[]>> {
    private final List<InternalSubscription> subscriptions = new CopyOnWriteArrayList<>();

    /**
     * Returns a list of subscriptions within the subscriber.
     *
     * @return a list of subscriptions
     */
    List<InternalSubscription> subscriptions() {
      return ImmutableList.copyOf(subscriptions);
    }

    @Override
    public CompletableFuture<byte[]> apply(final Address address, final byte[] payload) {
      for (final InternalSubscription s : subscriptions) {
        s.callback.apply(payload);
      }
      return CompletableFuture.completedFuture(null);
    }

    /**
     * Adds a local subscription.
     *
     * @param subscription the subscription to add
     */
    void add(final InternalSubscription subscription) {
      subscriptions.add(subscription);
    }

    /**
     * Removes a local subscription.
     *
     * @param subscription the subscription to remove
     */
    void remove(final InternalSubscription subscription) {
      subscriptions.remove(subscription);
    }
  }

  /** Internal topic. */
  private class InternalTopic {
    private final String topic;
    private final InternalSubscriber localSubscribers = new InternalSubscriber();
    private final Set<MemberId> subscriptions = Sets.newCopyOnWriteArraySet();

    InternalTopic(final String topic) {
      this.topic = topic;
    }

    /**
     * Returns the local subscriber for the topic.
     *
     * @return the local subscriber for the topic
     */
    InternalSubscriber localSubscriber() {
      return localSubscribers;
    }

    /**
     * Returns the list of remote subscriptions for the topic.
     *
     * @return the list of remote subscriptions for the topic
     */
    Set<MemberId> remoteSubscriptions() {
      return subscriptions;
    }

    /** Subscribes to messages from the topic. */
    <M, R> CompletableFuture<Subscription> subscribe(
        final Function<byte[], M> decoder,
        final Function<M, R> handler,
        final Function<R, byte[]> encoder,
        final Executor executor) {
      return addLocalSubscription(
          new InternalSubscription(
              this,
              payload -> {
                final CompletableFuture<byte[]> future = new CompletableFuture<>();
                executor.execute(
                    () -> {
                      try {
                        future.complete(encoder.apply(handler.apply(decoder.apply(payload))));
                      } catch (final Exception e) {
                        future.completeExceptionally(e);
                      }
                    });
                return future;
              }));
    }

    /** Subscribes to messages from the topic. */
    <M, R> CompletableFuture<Subscription> subscribe(
        final Function<byte[], M> decoder,
        final Function<M, CompletableFuture<R>> handler,
        final Function<R, byte[]> encoder) {
      return addLocalSubscription(
          new InternalSubscription(
              this, payload -> handler.apply(decoder.apply(payload)).thenApply(encoder)));
    }

    /** Subscribes to messages from the topic. */
    <M> CompletableFuture<Subscription> subscribe(
        final Function<byte[], M> decoder, final Consumer<M> handler, final Executor executor) {
      return addLocalSubscription(
          new InternalSubscription(
              this,
              payload -> {
                executor.execute(
                    () -> {
                      final M decoded;
                      try {
                        decoded = decoder.apply(payload);
                      } catch (final RuntimeException e) {
                        LOGGER.error("Failed to decode message payload for topic {}", topic, e);
                        return;
                      }

                      try {
                        handler.accept(decoded);
                      } catch (final RuntimeException e) {
                        LOGGER.error("Failed to handle message {} for topic {}", decoded, topic, e);
                      }
                    });
                return CompletableFuture.completedFuture(null);
              }));
    }

    /**
     * Registers the node as a subscriber for the given topic.
     *
     * @param subscription the subscription to register
     */
    private synchronized CompletableFuture<Subscription> addLocalSubscription(
        final InternalSubscription subscription) {
      if (localSubscribers.subscriptions.isEmpty()) {
        messagingService.registerHandler(subscription.topic(), localSubscribers);
      }
      localSubscribers.add(subscription);
      subscriptions.add(localMemberId);
      return updateNodes().thenApply(v -> subscription);
    }

    /**
     * Unregisters the node as a subscriber for the given topic.
     *
     * @param subscription the subscription to unregister
     */
    private synchronized CompletableFuture<Void> removeLocalSubscription(
        final InternalSubscription subscription) {
      localSubscribers.remove(subscription);
      if (localSubscribers.subscriptions.isEmpty()) {
        subscriptions.remove(localMemberId);
        messagingService.unregisterHandler(subscription.topic());
      }
      return updateNodes();
    }

    /**
     * Adds a subscription to the topic.
     *
     * @param subscription the subscription to add
     */
    void addRemoteSubscription(final MemberId subscription) {
      subscriptions.add(subscription);
    }

    /**
     * Updates a subscription to the topic.
     *
     * @param subscription the subscription to update
     */
    void removeRemoteSubscription(final MemberId subscription) {
      subscriptions.remove(subscription);
    }
  }

  /** Internal subscription. */
  private class InternalSubscription implements Subscription {
    private final InternalTopic topic;
    private final Function<byte[], CompletableFuture<byte[]>> callback;

    InternalSubscription(
        final InternalTopic topic, final Function<byte[], CompletableFuture<byte[]>> callback) {
      this.topic = topic;
      this.callback = callback;
    }

    @Override
    public String topic() {
      return topic.topic;
    }

    @Override
    public CompletableFuture<Void> close() {
      return topic.removeLocalSubscription(this);
    }
  }
}
