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

import com.google.common.collect.Lists;
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Netty unicast service. */
public class NettyUnicastService implements ManagedUnicastService {
  private static final Logger LOGGER = LoggerFactory.getLogger(NettyUnicastService.class);
  private static final Serializer SERIALIZER =
      Serializer.using(
          Namespace.builder()
              .register(Namespaces.BASIC)
              .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
              .register(Message.class)
              .register(new AddressSerializer(), Address.class)
              .build());

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final Address address;
  private final MessagingConfig config;
  private EventLoopGroup group;
  private DatagramChannel channel;

  private final Map<String, Map<BiConsumer<Address, byte[]>, Executor>> listeners =
      Maps.newConcurrentMap();
  private final AtomicBoolean started = new AtomicBoolean();

  public NettyUnicastService(final Address address, final MessagingConfig config) {
    this.address = address;
    this.config = config;
  }

  @Override
  public void unicast(final Address address, final String subject, final byte[] payload) {
    final InetAddress resolvedAddress = address.address();
    if (resolvedAddress == null) {
      LOGGER.debug(
          "Failed sending unicast message (destination address {} cannot be resolved)", address);
      return;
    }

    final Message message = new Message(this.address, subject, payload);
    final byte[] bytes = SERIALIZER.encode(message);
    final ByteBuf buf = channel.alloc().buffer(4 + bytes.length);
    buf.writeInt(bytes.length).writeBytes(bytes);
    channel.writeAndFlush(
        new DatagramPacket(buf, new InetSocketAddress(resolvedAddress, address.port())));
  }

  @Override
  public synchronized void addListener(
      final String subject, final BiConsumer<Address, byte[]> listener, final Executor executor) {
    listeners.computeIfAbsent(subject, s -> Maps.newConcurrentMap()).put(listener, executor);
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
                      final ChannelHandlerContext context, final DatagramPacket packet)
                      throws Exception {
                    final byte[] payload = new byte[packet.content().readInt()];
                    packet.content().readBytes(payload);
                    final Message message = SERIALIZER.decode(payload);
                    final Map<BiConsumer<Address, byte[]>, Executor> listeners =
                        NettyUnicastService.this.listeners.get(message.subject());
                    if (listeners != null) {
                      listeners.forEach(
                          (consumer, executor) ->
                              executor.execute(
                                  () -> consumer.accept(message.source(), message.payload())));
                    }
                  }
                })
            .option(ChannelOption.RCVBUF_ALLOCATOR, new DefaultMaxBytesRecvByteBufAllocator())
            .option(ChannelOption.SO_BROADCAST, true)
            .option(ChannelOption.SO_REUSEADDR, true);

    return bind(serverBootstrap);
  }

  /**
   * Binds the given bootstrap to the appropriate interfaces.
   *
   * @param bootstrap the bootstrap to bind
   * @return a future to be completed once the bootstrap has been bound to all interfaces
   */
  private CompletableFuture<Void> bind(final Bootstrap bootstrap) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    final int port = config.getPort() != null ? config.getPort() : address.port();
    if (config.getInterfaces().isEmpty()) {
      bind(bootstrap, Lists.newArrayList("0.0.0.0").iterator(), port, future);
    } else {
      bind(bootstrap, config.getInterfaces().iterator(), port, future);
    }
    return future;
  }

  /**
   * Recursively binds the given bootstrap to the given interfaces.
   *
   * @param bootstrap the bootstrap to bind
   * @param ifaces an iterator of interfaces to which to bind
   * @param port the port to which to bind
   * @param future the future to completed once the bootstrap has been bound to all provided
   *     interfaces
   */
  private void bind(
      final Bootstrap bootstrap,
      final Iterator<String> ifaces,
      final int port,
      final CompletableFuture<Void> future) {
    if (ifaces.hasNext()) {
      final String iface = ifaces.next();
      bootstrap
          .bind(iface, port)
          .addListener(
              (ChannelFutureListener)
                  f -> {
                    if (f.isSuccess()) {
                      log.info("UDP server listening for connections on {}:{}", iface, port);
                      channel = (DatagramChannel) f.channel();
                      bind(bootstrap, ifaces, port, future);
                    } else {
                      log.warn(
                          "Failed to bind TCP server to port {}:{} due to {}",
                          iface,
                          port,
                          f.cause());
                      future.completeExceptionally(f.cause());
                    }
                  });
    } else {
      future.complete(null);
    }
  }

  @Override
  public CompletableFuture<UnicastService> start() {
    group = new NioEventLoopGroup(0, namedThreads("netty-unicast-event-nio-client-%d", log));
    return bootstrap().thenRun(() -> started.set(true)).thenApply(v -> this);
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public CompletableFuture<Void> stop() {
    if (channel != null) {
      final CompletableFuture<Void> future = new CompletableFuture<>();
      channel
          .close()
          .addListener(
              f -> {
                started.set(false);
                group.shutdownGracefully();
                future.complete(null);
              });
      return future;
    }
    started.set(false);
    return CompletableFuture.completedFuture(null);
  }

  /** Internal unicast service message. */
  static class Message {
    private final Address source;
    private final String subject;
    private final byte[] payload;

    Message() {
      this(null, null, null);
    }

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
