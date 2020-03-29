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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ManagedClusterCommunicationService;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.messaging.UnicastService;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.net.Address;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cluster communication service implementation. */
public class DefaultClusterCommunicationService implements ManagedClusterCommunicationService {

  private static final Exception CONNECT_EXCEPTION = new ConnectException();

  static {
    CONNECT_EXCEPTION.setStackTrace(new StackTraceElement[0]);
  }

  protected final ClusterMembershipService membershipService;
  protected final MessagingService messagingService;
  protected final UnicastService unicastService;
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final Map<String, BiConsumer<Address, byte[]>> unicastConsumers = Maps.newConcurrentMap();
  private final AtomicBoolean started = new AtomicBoolean();

  public DefaultClusterCommunicationService(
      final ClusterMembershipService membershipService,
      final MessagingService messagingService,
      final UnicastService unicastService) {
    this.membershipService = checkNotNull(membershipService, "clusterService cannot be null");
    this.messagingService = checkNotNull(messagingService, "messagingService cannot be null");
    this.unicastService = checkNotNull(unicastService, "unicastService cannot be null");
  }

  @Override
  public <M> void broadcast(
      final String subject,
      final M message,
      final Function<M, byte[]> encoder,
      final boolean reliable) {
    multicast(
        subject,
        message,
        encoder,
        membershipService.getMembers().stream()
            .filter(node -> !Objects.equal(node, membershipService.getLocalMember()))
            .map(Member::id)
            .collect(Collectors.toSet()),
        reliable);
  }

  @Override
  public <M> void broadcastIncludeSelf(
      final String subject,
      final M message,
      final Function<M, byte[]> encoder,
      final boolean reliable) {
    multicast(
        subject,
        message,
        encoder,
        membershipService.getMembers().stream().map(Member::id).collect(Collectors.toSet()),
        reliable);
  }

  @Override
  public <M> CompletableFuture<Void> unicast(
      final String subject,
      final M message,
      final Function<M, byte[]> encoder,
      final MemberId toMemberId,
      final boolean reliable) {
    try {
      return doUnicast(subject, encoder.apply(message), toMemberId, reliable);
    } catch (final Exception e) {
      return Futures.exceptionalFuture(e);
    }
  }

  @Override
  public <M> void multicast(
      final String subject,
      final M message,
      final Function<M, byte[]> encoder,
      final Set<MemberId> nodes,
      final boolean reliable) {
    final byte[] payload = encoder.apply(message);
    nodes.forEach(memberId -> doUnicast(subject, payload, memberId, reliable));
  }

  @Override
  public <M, R> CompletableFuture<R> send(
      final String subject,
      final M message,
      final Function<M, byte[]> encoder,
      final Function<byte[], R> decoder,
      final MemberId toMemberId,
      final Duration timeout) {
    try {
      return sendAndReceive(subject, encoder.apply(message), toMemberId, timeout)
          .thenApply(decoder);
    } catch (final Exception e) {
      return Futures.exceptionalFuture(e);
    }
  }

  @Override
  public <M, R> CompletableFuture<Void> subscribe(
      final String subject,
      final Function<byte[], M> decoder,
      final Function<M, R> handler,
      final Function<R, byte[]> encoder,
      final Executor executor) {
    messagingService.registerHandler(
        subject,
        new InternalMessageResponder<M, R>(
            decoder,
            encoder,
            m -> {
              final CompletableFuture<R> responseFuture = new CompletableFuture<>();
              executor.execute(
                  () -> {
                    try {
                      responseFuture.complete(handler.apply(m));
                    } catch (final Exception e) {
                      responseFuture.completeExceptionally(e);
                    }
                  });
              return responseFuture;
            }));
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public <M, R> CompletableFuture<Void> subscribe(
      final String subject,
      final Function<byte[], M> decoder,
      final Function<M, CompletableFuture<R>> handler,
      final Function<R, byte[]> encoder) {
    messagingService.registerHandler(
        subject, new InternalMessageResponder<>(decoder, encoder, handler));
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public <M> CompletableFuture<Void> subscribe(
      final String subject,
      final Function<byte[], M> decoder,
      final Consumer<M> handler,
      final Executor executor) {
    messagingService.registerHandler(
        subject, new InternalMessageConsumer<>(decoder, handler), executor);
    final BiConsumer<Address, byte[]> unicastConsumer =
        new InternalMessageConsumer<>(decoder, handler);
    unicastConsumers.put(subject, unicastConsumer);
    unicastService.addListener(subject, unicastConsumer, executor);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public <M> CompletableFuture<Void> subscribe(
      final String subject,
      final Function<byte[], M> decoder,
      final BiConsumer<MemberId, M> handler,
      final Executor executor) {
    messagingService.registerHandler(
        subject, new InternalMessageBiConsumer<>(decoder, handler), executor);
    final BiConsumer<Address, byte[]> unicastConsumer =
        new InternalMessageBiConsumer<>(decoder, handler);
    unicastConsumers.put(subject, unicastConsumer);
    unicastService.addListener(subject, unicastConsumer, executor);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void unsubscribe(final String subject) {
    messagingService.unregisterHandler(subject);
    final BiConsumer<Address, byte[]> consumer = unicastConsumers.remove(subject);
    if (consumer != null) {
      unicastService.removeListener(subject, consumer);
    }
  }

  private CompletableFuture<Void> doUnicast(
      final String subject,
      final byte[] payload,
      final MemberId toMemberId,
      final boolean reliable) {
    final Member member = membershipService.getMember(toMemberId);
    if (member == null) {
      return Futures.exceptionalFuture(CONNECT_EXCEPTION);
    }
    if (reliable) {
      return messagingService.sendAsync(member.address(), subject, payload);
    } else {
      unicastService.unicast(member.address(), subject, payload);
      return CompletableFuture.completedFuture(null);
    }
  }

  private CompletableFuture<byte[]> sendAndReceive(
      final String subject,
      final byte[] payload,
      final MemberId toMemberId,
      final Duration timeout) {
    final Member member = membershipService.getMember(toMemberId);
    if (member == null) {
      return Futures.exceptionalFuture(CONNECT_EXCEPTION);
    }
    return messagingService.sendAndReceive(member.address(), subject, payload, timeout);
  }

  @Override
  public CompletableFuture<ClusterCommunicationService> start() {
    if (started.compareAndSet(false, true)) {
      log.info("Started");
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
      log.info("Stopped");
    }
    return CompletableFuture.completedFuture(null);
  }

  private static class InternalMessageResponder<M, R>
      implements BiFunction<Address, byte[], CompletableFuture<byte[]>> {
    private final Function<byte[], M> decoder;
    private final Function<R, byte[]> encoder;
    private final Function<M, CompletableFuture<R>> handler;

    InternalMessageResponder(
        final Function<byte[], M> decoder,
        final Function<R, byte[]> encoder,
        final Function<M, CompletableFuture<R>> handler) {
      this.decoder = decoder;
      this.encoder = encoder;
      this.handler = handler;
    }

    @Override
    public CompletableFuture<byte[]> apply(final Address sender, final byte[] bytes) {
      return handler.apply(decoder.apply(bytes)).thenApply(encoder);
    }
  }

  private static class InternalMessageConsumer<M> implements BiConsumer<Address, byte[]> {
    private final Function<byte[], M> decoder;
    private final Consumer<M> consumer;

    InternalMessageConsumer(final Function<byte[], M> decoder, final Consumer<M> consumer) {
      this.decoder = decoder;
      this.consumer = consumer;
    }

    @Override
    public void accept(final Address sender, final byte[] bytes) {
      consumer.accept(decoder.apply(bytes));
    }
  }

  private class InternalMessageBiConsumer<M> implements BiConsumer<Address, byte[]> {
    private final Function<byte[], M> decoder;
    private final BiConsumer<MemberId, M> consumer;

    InternalMessageBiConsumer(
        final Function<byte[], M> decoder, final BiConsumer<MemberId, M> consumer) {
      this.decoder = decoder;
      this.consumer = consumer;
    }

    @Override
    public void accept(final Address sender, final byte[] bytes) {
      final Member member = membershipService.getMember(sender);
      if (member != null) {
        consumer.accept(member.id(), decoder.apply(bytes));
      }
    }
  }
}
