/*
 * Copyright 2018-present Open Networking Foundation
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

import com.google.common.collect.Maps;
import io.atomix.cluster.impl.AddressSerializer;
import io.atomix.cluster.messaging.ManagedUnicastService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.UnicastService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import io.atomix.utils.serializer.Serializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMaxBytesRecvByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.resolver.dns.BiDnsQueryLifecycleObserverFactory;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.LoggingDnsQueryLifeCycleObserverFactory;
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Netty unicast service. */
public class NettyUnicastService implements ManagedUnicastService {
  private static final Logger LOGGER = LoggerFactory.getLogger(NettyUnicastService.class);
  private static final Serializer SERIALIZER =
      Serializer.using(
          new Namespace.Builder()
              .register(Namespaces.BASIC)
              .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
              .register(Message.class)
              .register(new AddressSerializer(), Address.class)
              .build());

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final Address advertisedAddress;
  private final MessagingConfig config;
  private final int preamble;
  private final Map<String, Map<BiConsumer<Address, byte[]>, Executor>> listeners =
      Maps.newConcurrentMap();
  private final AtomicBoolean started = new AtomicBoolean();
  private final Address bindAddress;

  private EventLoopGroup group;
  private DatagramChannel channel;

  private DnsAddressResolverGroup dnsAddressResolverGroup;

  private final String actorSchedulerName;

  public NettyUnicastService(
      final String clusterId, final Address advertisedAddress, final MessagingConfig config) {
    this(clusterId, advertisedAddress, config, "");
  }

  public NettyUnicastService(
      final String clusterId,
      final Address advertisedAddress,
      final MessagingConfig config,
      final String actorSchedulerName) {
    this.advertisedAddress = advertisedAddress;
    this.config = config;
    preamble = clusterId.hashCode();

    // as we use SO_BROADCAST, it's only possible to bind to wildcard without root privilege, so we
    // don't support binding to multiple interfaces here; wouldn't make sense anyway
    final var port = config.getPort() != null ? config.getPort() : advertisedAddress.port();
    bindAddress = new Address(new InetSocketAddress(port));
    this.actorSchedulerName = actorSchedulerName != null ? actorSchedulerName : "";
  }

  @Override
  public void unicast(final Address address, final String subject, final byte[] payload) {
    if (!started.get()) {
      LOGGER.debug("Failed sending unicast message, unicast service was not started.");
      return;
    }

    final Message message = new Message(advertisedAddress, subject, payload);
    final byte[] bytes = SERIALIZER.encode(message);
    final ByteBuf buf = channel.alloc().buffer(Integer.BYTES + Integer.BYTES + bytes.length);
    buf.writeInt(preamble);
    buf.writeInt(bytes.length).writeBytes(bytes);

    dnsAddressResolverGroup
        .getResolver(group.next())
        .resolve(address.socketAddress())
        .addListener(
            resolvedAddress -> {
              if (resolvedAddress.isSuccess()) {
                channel.writeAndFlush(
                    new DatagramPacket(buf, (InetSocketAddress) resolvedAddress.get()));
              } else {
                log.warn(
                    "Failed sending unicast message (destination address {} cannot be resolved)",
                    address,
                    resolvedAddress.exceptionNow());
                // Buffer needs to be released manually when not consumed by Netty.
                // Netty will take care of the clean-up if it is passed to the channel.
                buf.release();
              }
            });
  }

  @Override
  public synchronized void addListener(
      final String subject, final BiConsumer<Address, byte[]> listener, final Executor executor) {
    listeners.computeIfAbsent(subject, s -> new ConcurrentHashMap<>()).put(listener, executor);
  }

  @Override
  public synchronized void removeListener(
      final String subject, final BiConsumer<Address, byte[]> listener) {
    final Map<BiConsumer<Address, byte[]>, Executor> listeners = this.listeners.get(subject);
    if (listeners != null) {
      listeners.remove(listener);
      if (listeners.isEmpty()) {
        this.listeners.remove(subject);
      }
    }
  }

  private CompletableFuture<Void> bootstrap() {
    final Bootstrap serverBootstrap =
        new Bootstrap()
            .group(group)
            .channel(NioDatagramChannel.class)
            .handler(
                new SimpleChannelInboundHandler<DatagramPacket>() {
                  @Override
                  protected void channelRead0(
                      final ChannelHandlerContext context, final DatagramPacket packet) {
                    handleReceivedPacket(packet);
                  }
                })
            .option(ChannelOption.RCVBUF_ALLOCATOR, new DefaultMaxBytesRecvByteBufAllocator())
            .option(ChannelOption.SO_BROADCAST, true)
            .option(ChannelOption.SO_REUSEADDR, true);

    return bind(serverBootstrap);
  }

  private void handleReceivedPacket(final DatagramPacket packet) {
    final int preambleReceived = packet.content().readInt();
    if (preambleReceived != preamble) {
      log.warn(
          "Received unicast message from {} which is outside of the cluster. Ignoring the message.",
          packet.sender());
      return;
    }
    final byte[] payload = new byte[packet.content().readInt()];
    packet.content().readBytes(payload);
    final Message message = SERIALIZER.decode(payload);
    final Map<BiConsumer<Address, byte[]>, Executor> subjectListeners =
        listeners.get(message.subject());
    if (subjectListeners != null) {
      subjectListeners.forEach(
          (consumer, executor) ->
              executor.execute(() -> consumer.accept(message.source(), message.payload())));
    }
  }

  private CompletableFuture<Void> bind(final Bootstrap bootstrap) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    bootstrap
        .bind(bindAddress.host(), bindAddress.port())
        .addListener(
            (ChannelFutureListener)
                f -> {
                  if (!f.isSuccess()) {
                    future.completeExceptionally(f.cause());
                    return;
                  }

                  channel = (DatagramChannel) f.channel();
                  future.complete(null);
                });

    return future;
  }

  @Override
  public CompletableFuture<UnicastService> start() {
    group =
        new NioEventLoopGroup(
            0, namedThreads("netty-unicast-event-nio-client-%d", log, actorSchedulerName));
    return bootstrap()
        .thenRun(
            () -> {
              final var metrics = new NettyDnsMetrics();
              started.set(true);
              dnsAddressResolverGroup =
                  new DnsAddressResolverGroup(
                      new DnsNameResolverBuilder(group.next())
                          .consolidateCacheSize(128)
                          .dnsQueryLifecycleObserverFactory(
                              new BiDnsQueryLifecycleObserverFactory(
                                  ignored -> metrics,
                                  new LoggingDnsQueryLifeCycleObserverFactory()))
                          .socketChannelType(NioSocketChannel.class)
                          .channelType(NioDatagramChannel.class));
            })
        .thenApply(
            v -> {
              log.info(
                  "Started plaintext unicast service bound to {}, advertising {}",
                  bindAddress,
                  advertisedAddress);
              return this;
            });
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public CompletableFuture<Void> stop() {
    if (!started.compareAndSet(true, false)) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.runAsync(this::doStop);
  }

  private void doStop() {
    boolean interrupted = false;
    if (channel != null) {

      try {
        channel.close().sync();
        if (dnsAddressResolverGroup != null) {
          CloseHelper.close(
              error -> log.warn("Failed to close DNS resolvers", error), dnsAddressResolverGroup);
        }
      } catch (final InterruptedException e) {
        interrupted = true;
      }

      channel = null;
    }

    final Future<?> shutdownFuture =
        group.shutdownGracefully(
            config.getShutdownQuietPeriod().toMillis(),
            config.getShutdownTimeout().toMillis(),
            TimeUnit.MILLISECONDS);
    try {
      shutdownFuture.sync();
    } catch (final InterruptedException e) {
      interrupted = true;
    }

    log.info(
        "Stopped plaintext unicast service bound to {}, advertising {}",
        bindAddress,
        advertisedAddress);

    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Internal unicast service message.
   *
   * <p>NOTE: Cannot be converted to a record as this would break kryo backwards compatibility
   */
  @SuppressWarnings("ClassCanBeRecord")
  static final class Message {
    private final Address source;
    private final String subject;
    private final byte[] payload;

    Message(final Address source, final String subject, final byte[] payload) {
      this.source = source;
      this.subject = subject;
      this.payload = payload;
    }

    Address source() {
      return source;
    }

    String subject() {
      return subject;
    }

    byte[] payload() {
      return payload;
    }
  }
}
