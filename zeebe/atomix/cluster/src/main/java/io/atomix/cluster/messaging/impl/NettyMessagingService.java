/*
 * Copyright 2015-present Open Networking Foundation
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
package io.atomix.cluster.messaging.impl;

import static io.atomix.utils.concurrent.Threads.namedThreads;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.MessagingException;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.utils.concurrent.OrderedFuture;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.util.StringUtil;
import io.camunda.zeebe.util.TlsConfigUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.compression.SnappyFrameDecoder;
import io.netty.handler.codec.compression.SnappyFrameEncoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.resolver.dns.BiDnsQueryLifecycleObserverFactory;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.LoggingDnsQueryLifeCycleObserverFactory;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Netty based MessagingService. */
public final class NettyMessagingService implements ManagedMessagingService {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
  private static final String TLS_PROTOCOL = "TLSv1.3";
  private static final String MESSAGE_DISPATCHER_NAME = "handler";

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final Address advertisedAddress;
  private final Collection<Address> bindingAddresses = new ArrayList<>();
  private final int preamble;
  private final ProtocolVersion protocolVersion;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final HandlerRegistry handlers = new HandlerRegistry();
  private final Map<Channel, RemoteClientConnection> connections = Maps.newConcurrentMap();
  private final AtomicLong messageIdGenerator = new AtomicLong(0);
  private final ChannelPool channelPool;
  private final Set<CompletableFuture<?>> openFutures = Sets.newConcurrentHashSet();
  private final MessagingConfig config;
  private EventLoopGroup serverGroup;
  private EventLoopGroup clientGroup;
  private Class<? extends ServerChannel> serverChannelClass;
  private Class<? extends SocketChannel> clientChannelClass;
  private Class<? extends DatagramChannel> clientDataGramChannelClass;
  private Channel serverChannel;
  // a single thread executor which silently rejects tasks being submitted when it's shutdown
  private ScheduledExecutorService timeoutExecutor;
  private volatile LocalClientConnection localConnection;
  private SslContext serverSslContext;
  private SslContext clientSslContext;
  private DnsAddressResolverGroup dnsResolverGroup;
  private final MessagingMetrics messagingMetrics;
  private final MeterRegistry registry;
  private final String actorSchedulerName;

  // flag for passing heartbeats down the pipeline
  private boolean forwardHeartbeats = false;
  private boolean heartbeatsEnabled = true;

  public NettyMessagingService(
      final String cluster,
      final Address advertisedAddress,
      final MessagingConfig config,
      final MeterRegistry registry) {
    this(cluster, advertisedAddress, config, ProtocolVersion.latest(), "", registry);
  }

  public NettyMessagingService(
      final String cluster,
      final Address advertisedAddress,
      final MessagingConfig config,
      final String actorSchedulerName,
      final MeterRegistry registry) {
    this(
        cluster, advertisedAddress, config, ProtocolVersion.latest(), actorSchedulerName, registry);
  }

  NettyMessagingService(
      final String cluster,
      final Address advertisedAddress,
      final MessagingConfig config,
      final ProtocolVersion protocolVersion,
      final String actorSchedulerName,
      final MeterRegistry registry) {
    preamble = cluster.hashCode();
    this.advertisedAddress = advertisedAddress;
    this.protocolVersion = protocolVersion;
    this.config = verifyHeartbeatConfig(config);
    // pool of client connections
    channelPool = new ChannelPool(this::openChannel);
    this.actorSchedulerName = actorSchedulerName;
    messagingMetrics = new MessagingMetricsImpl(registry);
    this.registry = registry;

    initAddresses(config);
  }

  @VisibleForTesting
  public ChannelPool getChannelPool() {
    return channelPool;
  }

  private static MessagingConfig verifyHeartbeatConfig(final MessagingConfig config) {
    if (config.getHeartbeatInterval().isZero() && config.getHeartbeatTimeout().isZero()) {
      // Setting both to zero essentially disables the heartbeat mechanism which is valid.
      return config;
    }

    if (config.getHeartbeatInterval().isNegative() || config.getHeartbeatTimeout().isNegative()) {
      throw new IllegalArgumentException(
          "Heartbeat interval and timeout must not be negative. Use 0s to disable heartbeats.");
    }

    if (config.getHeartbeatInterval().compareTo(config.getHeartbeatTimeout()) >= 0) {
      throw new IllegalArgumentException(
          "Heartbeat interval %s must be less than heartbeat timeout %s"
              .formatted(config.getHeartbeatInterval(), config.getHeartbeatTimeout()));
    }
    return config;
  }

  private void initAddresses(final MessagingConfig config) {
    final int port = config.getPort() != null ? config.getPort() : advertisedAddress.port();
    if (config.getInterfaces().isEmpty()) {
      bindingAddresses.add(Address.from(advertisedAddress.host(), port));
    } else {
      final List<Address> addresses =
          config.getInterfaces().stream()
              .map(iface -> Address.from(iface, port))
              .collect(Collectors.toList());
      bindingAddresses.addAll(addresses);
    }
  }

  @Override
  public Address address() {
    return advertisedAddress;
  }

  @Override
  public Collection<Address> bindingAddresses() {
    return bindingAddresses;
  }

  @Override
  public CompletableFuture<Void> sendAsync(
      final Address address, final String type, final byte[] payload, final boolean keepAlive) {
    final long messageId = messageIdGenerator.incrementAndGet();
    final ProtocolRequest message =
        new ProtocolRequest(messageId, advertisedAddress, type, payload);
    return executeOnPooledConnection(
        address, type, c -> c.sendAsync(message), MoreExecutors.directExecutor());
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address, final String type, final byte[] payload, final boolean keepAlive) {
    return sendAndReceive(
        address, type, payload, keepAlive, DEFAULT_TIMEOUT, MoreExecutors.directExecutor());
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Executor executor) {
    return sendAndReceive(address, type, payload, keepAlive, DEFAULT_TIMEOUT, executor);
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Duration timeout) {
    return sendAndReceive(
        address, type, payload, keepAlive, timeout, MoreExecutors.directExecutor());
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Duration timeout,
      final Executor executor) {
    if (!started.get()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("MessagingService is closed."));
    }

    final long messageId = messageIdGenerator.incrementAndGet();
    final ProtocolRequest message =
        new ProtocolRequest(messageId, advertisedAddress, type, payload);
    final CompletableFuture<byte[]> responseFuture;
    if (keepAlive) {
      responseFuture =
          executeOnPooledConnection(address, type, c -> c.sendAndReceive(message), executor);
    } else {
      responseFuture =
          executeOnTransientConnection(address, c -> c.sendAndReceive(message), executor);
    }

    final var timeoutFuture =
        timeoutExecutor.schedule(
            () -> {
              responseFuture.completeExceptionally(
                  new TimeoutException(
                      String.format("Request %s to %s timed out in %s", type, address, timeout)));
              openFutures.remove(responseFuture);
            },
            timeout.toNanos(),
            TimeUnit.NANOSECONDS);
    responseFuture.whenComplete((ignored, error) -> timeoutFuture.cancel(true));

    return responseFuture;
  }

  @Override
  public void registerHandler(
      final String type, final BiConsumer<Address, byte[]> handler, final Executor executor) {
    handlers.register(
        type,
        (message, connection) ->
            executor.execute(() -> handler.accept(message.sender(), message.payload())));
  }

  @Override
  public void registerHandler(
      final String type,
      final BiFunction<Address, byte[], byte[]> handler,
      final Executor executor) {
    handlers.register(
        type,
        (message, connection) ->
            executor.execute(
                () -> {
                  byte[] responsePayload = null;
                  ProtocolReply.Status status = ProtocolReply.Status.OK;
                  try {
                    responsePayload = handler.apply(message.sender(), message.payload());
                  } catch (final Exception e) {
                    log.warn(
                        "Unexpected error while handling message {} from {}",
                        message.subject(),
                        message.sender(),
                        e);

                    status = ProtocolReply.Status.ERROR_HANDLER_EXCEPTION;
                    final String exceptionMessage = e.getMessage();
                    if (exceptionMessage != null) {
                      responsePayload = StringUtil.getBytes(exceptionMessage);
                    }
                  }
                  connection.reply(message.id(), status, Optional.ofNullable(responsePayload));
                }));
  }

  @Override
  public void registerHandler(
      final String type, final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler) {
    handlers.register(
        type,
        (message, connection) -> {
          // Extract message components here to avoid retaining a reference to the entire message.
          // This means we don't need to retain the message payload until the response callback is
          // completed.
          final var id = message.id();
          final var subject = message.subject();
          final var sender = message.sender();
          final var payload = message.payload();
          handler
              .apply(sender, payload)
              .whenComplete(
                  (result, error) -> {
                    byte[] responsePayload = null;
                    final ProtocolReply.Status status;

                    if (error == null) {
                      status = ProtocolReply.Status.OK;
                      responsePayload = result;
                    } else {
                      log.warn(
                          "Unexpected error while handling message {} from {}",
                          subject,
                          sender,
                          error);

                      status = ProtocolReply.Status.ERROR_HANDLER_EXCEPTION;
                      final String exceptionMessage = error.getMessage();
                      if (exceptionMessage != null) {
                        responsePayload = StringUtil.getBytes(error.getMessage());
                      }
                    }
                    connection.reply(id, status, Optional.ofNullable(responsePayload));
                  });
        });
  }

  @Override
  public void unregisterHandler(final String type) {
    handlers.unregister(type);
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public CompletableFuture<MessagingService> start() {
    if (started.get()) {
      log.warn("Already running at local address: {}", advertisedAddress);
      return CompletableFuture.completedFuture(this);
    }

    final CompletableFuture<Void> serviceLoader;
    if (config.isTlsEnabled()) {
      serviceLoader = loadServerSslContext().thenCompose(ok -> loadClientSslContext());
    } else {
      serviceLoader = CompletableFuture.completedFuture(null);
    }

    initTransport();
    return serviceLoader
        .thenCompose(ok -> bootstrapServer())
        .thenRun(
            () -> {
              final var metrics = new NettyDnsMetrics(registry);
              dnsResolverGroup =
                  new DnsAddressResolverGroup(
                      new DnsNameResolverBuilder(clientGroup.next())
                          .consolidateCacheSize(128)
                          .dnsQueryLifecycleObserverFactory(
                              new BiDnsQueryLifecycleObserverFactory(
                                  ignored -> metrics,
                                  new LoggingDnsQueryLifeCycleObserverFactory()))
                          .socketChannelType(clientChannelClass)
                          .channelType(clientDataGramChannelClass));
              timeoutExecutor =
                  Executors.newSingleThreadScheduledExecutor(
                      new DefaultThreadFactory("netty-messaging-timeout-"));
              localConnection = new LocalClientConnection(handlers);
              started.set(true);
              log.info(
                  "Started messaging service bound to {}, advertising {}, and using {}",
                  bindingAddresses,
                  advertisedAddress,
                  config.isTlsEnabled() ? "TLS" : "plaintext");
            })
        .thenApply(v -> this);
  }

  @Override
  public CompletableFuture<Void> stop() {
    if (started.compareAndSet(true, false)) {
      return CompletableFuture.supplyAsync(
          () -> {
            boolean interrupted = false;
            try {
              if (dnsResolverGroup != null) {
                CloseHelper.close(
                    error -> log.warn("Failed to close DNS resolvers", error), dnsResolverGroup);
              }

              try {
                serverChannel.close().sync();
              } catch (final InterruptedException e) {
                interrupted = true;
              }
              final Future<?> serverShutdownFuture =
                  serverGroup.shutdownGracefully(
                      config.getShutdownQuietPeriod().toMillis(),
                      config.getShutdownTimeout().toMillis(),
                      TimeUnit.MILLISECONDS);
              final Future<?> clientShutdownFuture =
                  clientGroup.shutdownGracefully(
                      config.getShutdownQuietPeriod().toMillis(),
                      config.getShutdownTimeout().toMillis(),
                      TimeUnit.MILLISECONDS);
              try {
                serverShutdownFuture.sync();
              } catch (final InterruptedException e) {
                interrupted = true;
              }
              try {
                clientShutdownFuture.sync();
              } catch (final InterruptedException e) {
                interrupted = true;
              }
              timeoutExecutor.shutdown();

              for (final var entry : connections.entrySet()) {
                final var channel = entry.getKey();
                channel.close();
                final var connection = entry.getValue();
                connection.close();
              }

              for (final var openFuture : openFutures) {
                openFuture.completeExceptionally(
                    new IllegalStateException("MessagingService has been closed."));
              }
              openFutures.clear();
            } finally {
              log.info(
                  "Stopped messaging service bound to {}, advertising {}, and using {}",
                  bindingAddresses,
                  advertisedAddress,
                  config.isTlsEnabled() ? "TLS" : "plaintext");
              if (interrupted) {
                Thread.currentThread().interrupt();
              }
            }
            return null;
          });
    }
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> loadClientSslContext() {
    try {

      final var sslContextBuilder = SslContextBuilder.forClient();

      if (config.getKeyStore() != null) {
        sslContextBuilder.trustManager(
            TlsConfigUtil.getCertificateChain(config.getKeyStore(), config.getKeyStorePassword()));
      } else {
        sslContextBuilder.trustManager(config.getCertificateChain());
      }
      clientSslContext =
          sslContextBuilder.sslProvider(SslProvider.OPENSSL_REFCNT).protocols(TLS_PROTOCOL).build();
      return CompletableFuture.completedFuture(null);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(
          new MessagingException(
              "Failed to start messaging service; invalid client TLS configuration", e));
    }
  }

  private CompletableFuture<Void> loadServerSslContext() {
    try {
      final SslContextBuilder sslContextBuilder;

      if (config.getKeyStore() != null) {
        final var privateKey =
            TlsConfigUtil.getPrivateKey(config.getKeyStore(), config.getKeyStorePassword());
        final var certChain =
            TlsConfigUtil.getCertificateChain(config.getKeyStore(), config.getKeyStorePassword());

        sslContextBuilder = SslContextBuilder.forServer(privateKey, certChain);
      } else {
        sslContextBuilder =
            SslContextBuilder.forServer(config.getCertificateChain(), config.getPrivateKey());
      }
      serverSslContext =
          sslContextBuilder.sslProvider(SslProvider.OPENSSL_REFCNT).protocols(TLS_PROTOCOL).build();
      return CompletableFuture.completedFuture(null);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(
          new MessagingException(
              "Failed to start messaging service; invalid server TLS configuration", e));
    }
  }

  private void initTransport() {
    if (Epoll.isAvailable()) {
      initEpollTransport();
    } else {
      initNioTransport();
    }
  }

  private X509Certificate[] getCertificateChain(final File keyStoreFile, final String password)
      throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
    final var keyStore = getKeyStore(keyStoreFile, password);

    final String alias = keyStore.aliases().nextElement();
    return Arrays.stream(keyStore.getCertificateChain(alias))
        .map(X509Certificate.class::cast)
        .toArray(X509Certificate[]::new);
  }

  private PrivateKey getPrivateKey(final File keyStoreFile, final String password)
      throws CertificateException,
          KeyStoreException,
          IOException,
          NoSuchAlgorithmException,
          UnrecoverableKeyException {
    final var keyStore = getKeyStore(keyStoreFile, password);

    final String alias = keyStore.aliases().nextElement();
    return (PrivateKey) keyStore.getKey(alias, password.toCharArray());
  }

  private KeyStore getKeyStore(final File keyStoreFile, final String password)
      throws KeyStoreException {
    final var keyStore = KeyStore.getInstance("PKCS12");
    try {
      keyStore.load(new FileInputStream(keyStoreFile), password.toCharArray());
    } catch (final Exception e) {
      throw new IllegalStateException(
          String.format(
              "Keystore failed to load file: %s, please ensure it is a valid PKCS12 keystore",
              keyStoreFile.toPath()),
          e);
    }

    return keyStore;
  }

  private void initEpollTransport() {
    clientGroup =
        new EpollEventLoopGroup(
            0, namedThreads("netty-messaging-event-epoll-client-%d", log, actorSchedulerName));
    serverGroup =
        new EpollEventLoopGroup(
            0, namedThreads("netty-messaging-event-epoll-server-%d", log, actorSchedulerName));
    serverChannelClass = EpollServerSocketChannel.class;
    clientChannelClass = EpollSocketChannel.class;
    clientDataGramChannelClass = EpollDatagramChannel.class;
  }

  private void initNioTransport() {
    clientGroup =
        new NioEventLoopGroup(
            0, namedThreads("netty-messaging-event-nio-client-%d", log, actorSchedulerName));
    serverGroup =
        new NioEventLoopGroup(
            0, namedThreads("netty-messaging-event-nio-server-%d", log, actorSchedulerName));
    serverChannelClass = NioServerSocketChannel.class;
    clientChannelClass = NioSocketChannel.class;
    clientDataGramChannelClass = NioDatagramChannel.class;
  }

  /**
   * Executes the given callback on a pooled connection.
   *
   * @param address the connection address
   * @param type the message type to map to the connection
   * @param callback the callback to execute
   * @param executor an executor on which to complete the callback future
   * @param <T> the callback response type
   * @return a future to be completed once the callback future is complete
   */
  private <T> CompletableFuture<T> executeOnPooledConnection(
      final Address address,
      final String type,
      final Function<ClientConnection, CompletableFuture<T>> callback,
      final Executor executor) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    executeOnPooledConnection(address, type, callback, executor, future);
    return future;
  }

  /**
   * Executes the given callback on a pooled connection.
   *
   * @param address the connection address
   * @param type the message type to map to the connection
   * @param callback the callback to execute
   * @param executor an executor on which to complete the callback future
   * @param responseFuture the future to be completed once the callback future is complete
   * @param <T> the callback response type
   */
  private <T> void executeOnPooledConnection(
      final Address address,
      final String type,
      final Function<ClientConnection, CompletableFuture<T>> callback,
      final Executor executor,
      final CompletableFuture<T> responseFuture) {
    if (address.equals(advertisedAddress)) {
      callback
          .apply(localConnection)
          .whenComplete(
              (result, error) -> {
                if (error == null) {
                  executor.execute(() -> responseFuture.complete(result));
                } else {
                  executor.execute(() -> responseFuture.completeExceptionally(error));
                }
              });
      return;
    }

    openFutures.add(responseFuture);
    channelPool
        .getChannel(address, type)
        .whenComplete(
            (channel, channelError) -> {
              if (channelError == null) {
                final ClientConnection connection = getOrCreateClientConnection(channel);
                callback
                    .apply(connection)
                    .whenComplete(
                        (result, sendError) -> {
                          if (sendError == null) {
                            executor.execute(
                                () -> {
                                  responseFuture.complete(result);
                                  openFutures.remove(responseFuture);
                                });
                          } else {
                            final Throwable cause = Throwables.getRootCause(sendError);
                            if (!(cause instanceof TimeoutException)
                                && !(cause instanceof MessagingException)) {
                              channel
                                  .close()
                                  .addListener(
                                      f -> {
                                        log.debug(
                                            "Closing connection to {}", channel.remoteAddress());
                                        connection.close();
                                        connections.remove(channel);
                                      });
                            }
                            executor.execute(
                                () -> {
                                  responseFuture.completeExceptionally(sendError);
                                  openFutures.remove(responseFuture);
                                });
                          }
                        });
              } else {
                executor.execute(
                    () -> {
                      responseFuture.completeExceptionally(channelError);
                      openFutures.remove(responseFuture);
                    });
              }
            });
  }

  /**
   * Executes the given callback on a transient connection.
   *
   * @param address the connection address
   * @param callback the callback to execute
   * @param executor an executor on which to complete the callback future
   * @param <T> the callback response type
   */
  private <T> CompletableFuture<T> executeOnTransientConnection(
      final Address address,
      final Function<ClientConnection, CompletableFuture<T>> callback,
      final Executor executor) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    if (address.equals(advertisedAddress)) {
      callback
          .apply(localConnection)
          .whenComplete(
              (result, error) -> {
                if (error == null) {
                  executor.execute(() -> future.complete(result));
                } else {
                  executor.execute(() -> future.completeExceptionally(error));
                }
              });
      return future;
    }

    openChannel(address)
        .whenComplete(
            (channel, channelError) -> {
              if (channelError == null) {
                callback
                    .apply(getOrCreateClientConnection(channel))
                    .whenComplete(
                        (result, sendError) -> {
                          if (sendError == null) {
                            executor.execute(() -> future.complete(result));
                          } else {
                            executor.execute(() -> future.completeExceptionally(sendError));
                          }
                          channel.close();
                        });
              } else {
                executor.execute(() -> future.completeExceptionally(channelError));
              }
            });
    return future;
  }

  private RemoteClientConnection getOrCreateClientConnection(final Channel channel) {
    RemoteClientConnection connection = connections.get(channel);
    if (connection == null) {
      connection =
          connections.computeIfAbsent(
              channel, c -> new RemoteClientConnection(messagingMetrics, c));
      channel
          .closeFuture()
          .addListener(
              f -> {
                final RemoteClientConnection removedConnection = connections.remove(channel);
                if (removedConnection != null) {
                  removedConnection.close();
                }
              });
    }
    return connection;
  }

  /**
   * Opens a new Netty channel to the given address.
   *
   * @param address the address to which to open the channel
   * @return a future to be completed once the channel has been opened and the handshake is complete
   */
  private CompletableFuture<Channel> openChannel(final Address address) {
    return bootstrapClient(address);
  }

  /**
   * Bootstraps a new channel to the given address.
   *
   * @param address the address to which to connect
   * @return a future to be completed with the connected channel
   */
  private CompletableFuture<Channel> bootstrapClient(final Address address) {
    final CompletableFuture<Channel> future = new OrderedFuture<>();
    final InetSocketAddress socketAddress = address.socketAddress();

    final Bootstrap bootstrap = new Bootstrap();
    bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
    bootstrap.option(
        ChannelOption.WRITE_BUFFER_WATER_MARK,
        new WriteBufferWaterMark(10 * 32 * 1024, 10 * 64 * 1024));
    if (config.getSocketReceiveBuffer() != MessagingConfig.AUTO_SOCKET_SIZE) {
      bootstrap.option(ChannelOption.SO_RCVBUF, config.getSocketReceiveBuffer());
    }
    if (config.getSocketSendBuffer() != MessagingConfig.AUTO_SOCKET_SIZE) {
      bootstrap.option(ChannelOption.SO_SNDBUF, config.getSocketSendBuffer());
    }
    bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
    bootstrap.option(ChannelOption.TCP_NODELAY, true);
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
    bootstrap.group(clientGroup);
    bootstrap.channel(clientChannelClass);
    bootstrap.resolver(dnsResolverGroup);
    bootstrap.remoteAddress(socketAddress);
    bootstrap.handler(new BasicClientChannelInitializer(future));

    final Channel channel =
        bootstrap
            .connect()
            .addListener(
                onConnect -> {
                  if (!onConnect.isSuccess()) {
                    future.completeExceptionally(
                        new ConnectException(
                            String.format(
                                "Failed to connect channel for address %s (resolved: %s) : %s",
                                address, address.getAddress(), onConnect.cause())));
                  }
                })
            .channel();

    // immediately ensure we're notified of the channel being closed. the common case is that the
    // channel is closed after we've handled the request (and response in the case of a
    // sendAndReceive operation), so the future is already completed by then. If it isn't, then the
    // channel was closed too early, which should be handled as a failure from the consumer point
    // of view.
    channel
        .closeFuture()
        .addListener(
            onClose -> {
              if (!future.isDone()) {
                future.completeExceptionally(
                    new MessagingException.ConnectionClosed(
                        String.format(
                            "Channel %s for address %s was closed unexpectedly before the request was handled",
                            channel, address)));
              }
            });

    return future;
  }

  /**
   * Bootstraps a server.
   *
   * @return a future to be completed once the server has been bound to all interfaces
   */
  private CompletableFuture<Void> bootstrapServer() {
    final ServerBootstrap b = new ServerBootstrap();
    b.option(ChannelOption.SO_REUSEADDR, true);
    b.option(ChannelOption.SO_BACKLOG, 128);
    b.childOption(
        ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024));
    if (config.getSocketReceiveBuffer() > 0) {
      b.option(ChannelOption.SO_RCVBUF, config.getSocketReceiveBuffer());
    }
    if (config.getSocketSendBuffer() > 0) {
      b.option(ChannelOption.SO_SNDBUF, config.getSocketSendBuffer());
    }
    b.childOption(ChannelOption.SO_KEEPALIVE, true);
    b.childOption(ChannelOption.TCP_NODELAY, true);
    b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
    b.group(serverGroup, clientGroup);
    b.channel(serverChannelClass);
    b.childHandler(new BasicServerChannelInitializer());
    return bind(b);
  }

  /**
   * Binds the given bootstrap to the appropriate interfaces.
   *
   * @param bootstrap the bootstrap to bind
   * @return a future to be completed once the bootstrap has been bound to all interfaces
   */
  private CompletableFuture<Void> bind(final ServerBootstrap bootstrap) {
    final CompletableFuture<Void> future = new CompletableFuture<>();

    bind(bootstrap, bindingAddresses.iterator(), future);

    return future;
  }

  /**
   * Recursively binds the given bootstrap to the given interfaces.
   *
   * @param bootstrap the bootstrap to bind
   * @param addressIterator an iterator of Addresses to which to bind
   * @param future the future to completed once the bootstrap has been bound to all provided
   *     interfaces
   */
  private void bind(
      final ServerBootstrap bootstrap,
      final Iterator<Address> addressIterator,
      final CompletableFuture<Void> future) {
    if (addressIterator.hasNext()) {
      final Address address = addressIterator.next();
      bootstrap
          .bind(address.host(), address.port())
          .addListener(
              (ChannelFutureListener)
                  f -> {
                    if (f.isSuccess()) {
                      serverChannel = f.channel();
                      bind(bootstrap, addressIterator, future);
                    } else {
                      log.warn(
                          "Failed to bind TCP server to port {} due to {}", address, f.cause());
                      future.completeExceptionally(f.cause());
                    }
                  });
    } else {
      future.complete(null);
    }
  }

  @VisibleForTesting
  void enableHeartbeatsForwarding() {
    forwardHeartbeats = true;
  }

  @VisibleForTesting
  void disableHeartbeats() {
    heartbeatsEnabled = false;
  }

  /** Channel initializer for basic connections. */
  private class BasicClientChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final CompletableFuture<Channel> future;

    BasicClientChannelInitializer(final CompletableFuture<Channel> future) {
      this.future = future;
    }

    @Override
    protected void initChannel(final SocketChannel channel) {
      if (config.isTlsEnabled()) {
        final var sslHandler = clientSslContext.newHandler(channel.alloc());
        channel.pipeline().addLast("tls", sslHandler);
      }

      channel.pipeline().addLast("handshake", new ClientHandshakeHandlerAdapter(future));

      switch (config.getCompressionAlgorithm()) {
        case GZIP:
          channel.pipeline().addLast(ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP));
          channel.pipeline().addLast(ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP));
          break;
        case SNAPPY:
          channel.pipeline().addLast(new SnappyFrameEncoder());
          channel.pipeline().addLast(new SnappyFrameDecoder());
          break;
        case NONE:
          break;
        default:
          log.debug("Unknown compression algorithm. Proceeding without compression.");
      }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause)
        throws Exception {
      future.completeExceptionally(cause);
      super.exceptionCaught(ctx, cause);
    }
  }

  /** Channel initializer for basic connections. */
  private final class BasicServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(final SocketChannel channel) {
      if (config.isTlsEnabled()) {
        final var sslHandler = serverSslContext.newHandler(channel.alloc());
        channel.pipeline().addLast("tls", sslHandler);
      }

      channel.pipeline().addLast("handshake", new ServerHandshakeHandlerAdapter());

      switch (config.getCompressionAlgorithm()) {
        case GZIP:
          channel.pipeline().addLast(ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP));
          channel.pipeline().addLast(ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP));
          break;
        case SNAPPY:
          channel.pipeline().addLast(new SnappyFrameEncoder());
          channel.pipeline().addLast(new SnappyFrameDecoder());
          break;
        case NONE:
          break;
        default:
          log.debug("Unknown compression algorithm. Proceeding without compression.");
      }
    }
  }

  /** Base class for handshake handlers. */
  private abstract class HandshakeHandlerAdapter<M extends ProtocolMessage>
      extends ChannelInboundHandlerAdapter {

    /**
     * Writes the protocol version to the given context.
     *
     * @param context the context to which to write the version
     * @param version the version to write
     */
    void writeProtocolVersion(final ChannelHandlerContext context, final ProtocolVersion version) {
      final ByteBuf buffer = context.alloc().buffer(6);
      buffer.writeInt(preamble);
      buffer.writeShort(version.version());
      context.writeAndFlush(buffer);
    }

    /**
     * Reads the protocol version from the given buffer.
     *
     * @param context the buffer context
     * @param buffer the buffer from which to read the version
     * @return the read protocol version
     */
    OptionalInt readProtocolVersion(final ChannelHandlerContext context, final ByteBuf buffer) {
      try {
        final int preamble = buffer.readInt();
        if (preamble != NettyMessagingService.this.preamble) {
          log.warn("Received invalid handshake, closing connection");
          context.close();
          return OptionalInt.empty();
        }
        return OptionalInt.of(buffer.readShort());
      } finally {
        buffer.release();
      }
    }

    /**
     * Activates the given version of the messaging protocol.
     *
     * @param context the channel handler context
     * @param connection the client or server connection for which to activate the protocol version
     * @param protocolVersion the protocol version to activate
     */
    void activateProtocolVersion(
        final ChannelHandlerContext context,
        final Connection<M> connection,
        final ProtocolVersion protocolVersion,
        final boolean isClient) {
      final MessagingProtocol protocol = protocolVersion.createProtocol(advertisedAddress);
      context.pipeline().remove(this);
      context.pipeline().addLast("encoder", protocol.newEncoder());
      context.pipeline().addLast("decoder", protocol.newDecoder());

      context.pipeline().addLast(MESSAGE_DISPATCHER_NAME, new MessageDispatcher<>(connection));
    }
  }

  /** Client handshake handler. */
  private class ClientHandshakeHandlerAdapter extends HandshakeHandlerAdapter<ProtocolReply> {

    private final CompletableFuture<Channel> future;

    ClientHandshakeHandlerAdapter(final CompletableFuture<Channel> future) {
      this.future = future;
    }

    @Override
    public void channelActive(final ChannelHandlerContext context) throws Exception {
      log.debug(
          "Writing client protocol version {} for connection to {}",
          protocolVersion,
          context.channel().remoteAddress());
      writeProtocolVersion(context, protocolVersion);
    }

    @Override
    public void channelRead(final ChannelHandlerContext context, final Object message)
        throws Exception {
      // Read the protocol version from the server.
      readProtocolVersion(context, (ByteBuf) message)
          .ifPresent(
              version -> {
                // If the protocol version is a valid protocol version for the client, activate the
                // protocol.
                // Otherwise, close the connection and log an error.
                final ProtocolVersion protocolVersion = ProtocolVersion.valueOf(version);
                if (protocolVersion != null) {
                  activateProtocolVersion(
                      context,
                      getOrCreateClientConnection(context.channel()),
                      protocolVersion,
                      true);
                } else {
                  log.error("Failed to negotiate protocol version");
                  context.close();
                }
              });
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause)
        throws Exception {
      future.completeExceptionally(cause);
    }

    @Override
    void activateProtocolVersion(
        final ChannelHandlerContext context,
        final Connection<ProtocolReply> connection,
        final ProtocolVersion protocolVersion,
        final boolean isClient) {
      log.debug(
          "Activating client protocol version {} for connection to {}",
          protocolVersion,
          context.channel().remoteAddress());
      super.activateProtocolVersion(context, connection, protocolVersion, isClient);
      if (heartbeatsEnabled) {
        context
            .pipeline()
            .addBefore(
                MESSAGE_DISPATCHER_NAME,
                "heartbeat-setup",
                new HeartbeatSetupHandler.Client(
                    "decoder",
                    log,
                    messageIdGenerator,
                    advertisedAddress,
                    config.getHeartbeatTimeout(),
                    config.getHeartbeatInterval(),
                    forwardHeartbeats));
      }
      future.complete(context.channel());
    }
  }

  /** Server handshake handler. */
  private final class ServerHandshakeHandlerAdapter
      extends HandshakeHandlerAdapter<ProtocolRequest> {

    @Override
    public void channelRead(final ChannelHandlerContext context, final Object message)
        throws Exception {
      // Read the protocol version from the client handshake. If the client's protocol version is
      // unknown
      // to the server, use the latest server protocol version.
      readProtocolVersion(context, (ByteBuf) message)
          .ifPresent(
              version -> {
                ProtocolVersion protocolVersion = ProtocolVersion.valueOf(version);
                if (protocolVersion == null) {
                  protocolVersion = ProtocolVersion.latest();
                }
                writeProtocolVersion(context, protocolVersion);
                activateProtocolVersion(
                    context,
                    new RemoteServerConnection(handlers, context.channel()),
                    protocolVersion,
                    false);
              });
    }

    @Override
    void activateProtocolVersion(
        final ChannelHandlerContext context,
        final Connection<ProtocolRequest> connection,
        final ProtocolVersion protocolVersion,
        final boolean isClient) {
      log.debug(
          "Activating server protocol version {} for connection to {}",
          protocolVersion,
          context.channel().remoteAddress());
      super.activateProtocolVersion(context, connection, protocolVersion, isClient);
      if (heartbeatsEnabled) {
        context
            .pipeline()
            .addBefore(
                MESSAGE_DISPATCHER_NAME,
                "heartbeat-setup",
                new HeartbeatSetupHandler.Server("decoder", log, forwardHeartbeats));
      }
    }
  }

  /** Connection message dispatcher. */
  private class MessageDispatcher<M extends ProtocolMessage>
      extends SimpleChannelInboundHandler<Object> {

    private final Connection<M> connection;

    MessageDispatcher(final Connection<M> connection) {
      this.connection = connection;
    }

    @Override
    public void channelInactive(final ChannelHandlerContext context) throws Exception {
      connection.close();
      context.close();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
      log.error("Exception inside channel handling pipeline", cause);
      connection.close();
      context.close();
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) {
      return msg instanceof ProtocolMessage;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void channelRead0(final ChannelHandlerContext ctx, final Object message)
        throws Exception {
      try {
        connection.dispatch((M) message);
      } catch (final RejectedExecutionException e) {
        log.warn("Unable to dispatch message due to {}", e.getMessage());
      } catch (final ClassCastException e) {
        log.error("Failed to dispatch message due to {}", e.getMessage());
      }
    }
  }
}
