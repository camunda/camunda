/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.cluster.messaging.grpc;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.utils.net.Address;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NettyTransportFactory implements TransportFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(NettyTransportFactory.class);
  private static final int CONNECT_TIMEOUT_MILLIS = 1_000;

  private static final int NETTY_THREAD_COUNT = 2;
  private static final int GRPC_THREAD_COUNT = 2;

  private final MessagingConfig config;
  private final Address advertisedAddress;
  private final String transportName;

  private final EventLoopGroup eventLoopGroup;
  private final ExecutorService grpcExecutor;
  private final Address loopback;

  NettyTransportFactory(
      final MessagingConfig config, final Address advertisedAddress, final String transportName) {
    this.config = Objects.requireNonNull(config);
    this.advertisedAddress = Objects.requireNonNull(advertisedAddress);
    this.transportName = Objects.requireNonNull(transportName);

    loopback = new Address("localhost", config.getPort());
    loopback.resolve();

    grpcExecutor = createGrpcExecutor();
    eventLoopGroup = createEventLoopGroup();
  }

  private ThreadPoolExecutor createGrpcExecutor() {
    return new ThreadPoolExecutor(
        1,
        GRPC_THREAD_COUNT,
        60,
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        new DefaultThreadFactory(transportName + "-grpc"));
  }

  @Override
  public void close() {
    try {
      eventLoopGroup.shutdownGracefully(
          config.getShutdownQuietPeriod().toNanos(),
          config.getShutdownTimeout().toNanos(),
          TimeUnit.NANOSECONDS);
    } catch (final Exception e) {
      LOGGER.warn("Failed to shutdown event loop group; this is most likely harmless", e);
    }

    try {
      grpcExecutor.shutdownNow();
      grpcExecutor.awaitTermination(config.getShutdownTimeout().toNanos(), TimeUnit.NANOSECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.warn("Failed to shutdown transport executor; this is most likely harmless", e);
    } catch (final Exception e) {
      LOGGER.warn("Failed to shutdown transport executor; this is most likely harmless", e);
    }
  }

  @Override
  public ServerBuilder<NettyServerBuilder> createServerBuilder(final Address address) {
    final var inetAddress = address.address(true);
    final var builder =
        NettyServerBuilder.forAddress(address.socketAddress())
            .bossEventLoopGroup(eventLoopGroup)
            .workerEventLoopGroup(eventLoopGroup)
            .maxInboundMessageSize(config.getMaxMessageSize())
            // allow long living connections
            .maxConnectionAge(Long.MAX_VALUE, TimeUnit.MINUTES)
            .keepAliveTime(10, TimeUnit.SECONDS)
            .keepAliveTimeout(20, TimeUnit.SECONDS)
            .permitKeepAliveTime(10, TimeUnit.SECONDS)
            .permitKeepAliveWithoutCalls(true)
            .executor(grpcExecutor);

    if (!inetAddress.isAnyLocalAddress() && !inetAddress.isLoopbackAddress()) {
      builder.addListenAddress(loopback.socketAddress());
    }

    pickServerChannel(builder);

    return builder;
  }

  @Override
  public ManagedChannelBuilder<NettyChannelBuilder> createClientBuilder(final Address address) {
    final NettyChannelBuilder builder;

    // use loopback if the target address is our advertised address; this is particularly useful
    // when configured behind a reverse proxy if, for example, the localhost doesn't know how to
    // resolve our advertised address. by doing this we optimize the round trip since the packet
    // doesn't have to go out. this is mostly useful for the embedded gateway
    if (advertisedAddress.equals(address)) {
      builder = NettyChannelBuilder.forAddress(loopback.socketAddress());
    } else {
      builder = NettyChannelBuilder.forAddress(address.resolve());
    }

    builder
        .usePlaintext()
        .eventLoopGroup(eventLoopGroup)
        .executor(grpcExecutor)
        .keepAliveWithoutCalls(true)
        .keepAliveTime(15, TimeUnit.SECONDS)
        .keepAliveTimeout(20, TimeUnit.SECONDS)
        .defaultLoadBalancingPolicy("round_robin")
        .maxInboundMessageSize(config.getMaxMessageSize())
        .withOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS);
    pickClientChannel(builder);

    return builder;
  }

  private EventLoopGroup createEventLoopGroup() {
    final var threadFactory = new DefaultThreadFactory(transportName + "-netty");

    if (Epoll.isAvailable()) {
      return new EpollEventLoopGroup(NETTY_THREAD_COUNT, threadFactory);
    }

    return new NioEventLoopGroup(NETTY_THREAD_COUNT, threadFactory);
  }

  private void pickServerChannel(final NettyServerBuilder builder) {
    if (Epoll.isAvailable()) {
      builder
          .channelFactory(EpollServerSocketChannel::new)
          .channelType(EpollServerSocketChannel.class);
    } else {
      builder.channelFactory(NioServerSocketChannel::new).channelType(NioServerSocketChannel.class);
    }
  }

  private void pickClientChannel(final NettyChannelBuilder builder) {
    if (Epoll.isAvailable()) {
      builder.channelFactory(EpollSocketChannel::new).channelType(EpollSocketChannel.class);
    } else {
      builder.channelFactory(NioSocketChannel::new).channelType(NioSocketChannel.class);
    }
  }
}
