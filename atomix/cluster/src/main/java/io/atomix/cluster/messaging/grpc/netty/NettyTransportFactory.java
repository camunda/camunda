package io.atomix.cluster.messaging.grpc.netty;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.grpc.TransportFactory;
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
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NettyTransportFactory implements TransportFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(NettyTransportFactory.class);

  private final MessagingConfig config;
  private final EventLoopGroup eventLoopGroup;
  private final ExecutorService executor;

  public NettyTransportFactory(final MessagingConfig config, final ExecutorService executor) {
    this.config = config;
    this.executor = executor;

    eventLoopGroup = createEventLoopGroup(2, "grpc-messaging-service-netty");
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
      executor.shutdownNow();
    } catch (final Exception e) {
      LOGGER.warn("Failed to shutdown transport executor; this is most likely harmless", e);
    }
  }

  @Override
  public ServerBuilder<NettyServerBuilder> createServerBuilder(final SocketAddress address) {
    final var builder =
        NettyServerBuilder.forAddress(address)
            .bossEventLoopGroup(eventLoopGroup)
            .workerEventLoopGroup(eventLoopGroup)
            .executor(executor);
    pickServerChannel(builder);

    return builder;
  }

  @Override
  public ManagedChannelBuilder<NettyChannelBuilder> createClientBuilder(
      final SocketAddress address) {
    final var builder =
        NettyChannelBuilder.forAddress(address)
            .usePlaintext()
            .eventLoopGroup(eventLoopGroup)
            .executor(executor)
            .withOption(ChannelOption.TCP_NODELAY, true)
            .withOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
    pickClientChannel(builder);

    return builder;
  }

  private EventLoopGroup createEventLoopGroup(final int threadCount, final String prefix) {
    final var threadFactory = new DefaultThreadFactory(prefix);

    if (Epoll.isAvailable()) {
      return new EpollEventLoopGroup(threadCount, threadFactory);
    }

    return new NioEventLoopGroup(threadCount, threadFactory);
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
