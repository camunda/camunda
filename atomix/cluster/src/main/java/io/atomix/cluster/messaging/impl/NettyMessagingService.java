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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.MessagingException;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.utils.AtomixRuntimeException;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.concurrent.OrderedFuture;
import io.atomix.utils.net.Address;
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
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.Future;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Netty based MessagingService. */
public class NettyMessagingService implements ManagedMessagingService {
  protected boolean enableNettyTls;
  protected TrustManagerFactory trustManager;
  protected KeyManagerFactory keyManager;
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final Address returnAddress;
  private final int preamble;
  private final MessagingConfig config;
  private final ProtocolVersion protocolVersion;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final HandlerRegistry handlers = new HandlerRegistry();
  private volatile LocalClientConnection localConnection;
  private final Map<Channel, RemoteClientConnection> connections = Maps.newConcurrentMap();
  private final AtomicLong messageIdGenerator = new AtomicLong(0);
  private final ChannelPool channelPool;
  private EventLoopGroup serverGroup;
  private EventLoopGroup clientGroup;
  private Class<? extends ServerChannel> serverChannelClass;
  private Class<? extends Channel> clientChannelClass;
  private ScheduledExecutorService timeoutExecutor;
  private Channel serverChannel;

  public NettyMessagingService(
      final String cluster, final Address address, final MessagingConfig config) {
    this(cluster, address, config, ProtocolVersion.latest());
  }

  NettyMessagingService(
      final String cluster,
      final Address address,
      final MessagingConfig config,
      final ProtocolVersion protocolVersion) {
    this.preamble = cluster.hashCode();
    this.returnAddress = address;
    this.config = config;
    this.protocolVersion = protocolVersion;
    this.channelPool = new ChannelPool(this::openChannel, config.getConnectionPoolSize());
  }

  @Override
  public Address address() {
    return returnAddress;
  }

  @Override
  public CompletableFuture<Void> sendAsync(
      final Address address, final String type, final byte[] payload, final boolean keepAlive) {
    final long messageId = messageIdGenerator.incrementAndGet();
    final ProtocolRequest message = new ProtocolRequest(messageId, returnAddress, type, payload);
    return executeOnPooledConnection(
        address, type, c -> c.sendAsync(message), MoreExecutors.directExecutor());
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address, final String type, final byte[] payload, final boolean keepAlive) {
    return sendAndReceive(address, type, payload, keepAlive, null, MoreExecutors.directExecutor());
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(
      final Address address,
      final String type,
      final byte[] payload,
      final boolean keepAlive,
      final Executor executor) {
    return sendAndReceive(address, type, payload, keepAlive, null, executor);
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
    final long messageId = messageIdGenerator.incrementAndGet();
    final ProtocolRequest message = new ProtocolRequest(messageId, returnAddress, type, payload);
    if (keepAlive) {
      return executeOnPooledConnection(
          address, type, c -> c.sendAndReceive(message, timeout), executor);
    } else {
      return executeOnTransientConnection(
          address, c -> c.sendAndReceive(message, timeout), executor);
    }
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
                    log.warn("An error occurred in a message handler: {}", e);
                    status = ProtocolReply.Status.ERROR_HANDLER_EXCEPTION;
                  }
                  connection.reply(message, status, Optional.ofNullable(responsePayload));
                }));
  }

  @Override
  public void registerHandler(
      final String type, final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler) {
    handlers.register(
        type,
        (message, connection) -> {
          handler
              .apply(message.sender(), message.payload())
              .whenComplete(
                  (result, error) -> {
                    final ProtocolReply.Status status;
                    if (error == null) {
                      status = ProtocolReply.Status.OK;
                    } else {
                      log.warn("An error occurred in a message handler: {}", error);
                      status = ProtocolReply.Status.ERROR_HANDLER_EXCEPTION;
                    }
                    connection.reply(message, status, Optional.ofNullable(result));
                  });
        });
  }

  @Override
  public void unregisterHandler(final String type) {
    handlers.unregister(type);
  }

  @Override
  public CompletableFuture<MessagingService> start() {
    if (started.get()) {
      log.warn("Already running at local address: {}", returnAddress);
      return CompletableFuture.completedFuture(this);
    }

    enableNettyTls = loadKeyStores();
    initEventLoopGroup();
    return bootstrapServer()
        .thenRun(
            () -> {
              timeoutExecutor =
                  Executors.newScheduledThreadPool(
                      4, namedThreads("netty-messaging-timeout-%d", log));
              localConnection = new LocalClientConnection(timeoutExecutor, handlers);
              started.set(true);
              log.info("Started");
            })
        .thenApply(v -> this);
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public CompletableFuture<Void> stop() {
    if (started.compareAndSet(true, false)) {
      return CompletableFuture.supplyAsync(
          () -> {
            boolean interrupted = false;
            try {
              try {
                serverChannel.close().sync();
              } catch (final InterruptedException e) {
                interrupted = true;
              }
              final Future<?> serverShutdownFuture = serverGroup.shutdownGracefully();
              final Future<?> clientShutdownFuture = clientGroup.shutdownGracefully();
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
            } finally {
              log.info("Stopped");
              if (interrupted) {
                Thread.currentThread().interrupt();
              }
            }
            return null;
          });
    }
    return CompletableFuture.completedFuture(null);
  }

  private boolean loadKeyStores() {
    if (!config.getTlsConfig().isEnabled()) {
      return false;
    }

    // Maintain a local copy of the trust and key managers in case anything goes wrong
    final TrustManagerFactory tmf;
    final KeyManagerFactory kmf;
    try {
      final String ksLocation = config.getTlsConfig().getKeyStore();
      final String tsLocation = config.getTlsConfig().getTrustStore();
      final char[] ksPwd = config.getTlsConfig().getKeyStorePassword().toCharArray();
      final char[] tsPwd = config.getTlsConfig().getTrustStorePassword().toCharArray();

      tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      final KeyStore ts = KeyStore.getInstance(KeyStore.getDefaultType());
      try (final FileInputStream fileInputStream = new FileInputStream(tsLocation)) {
        ts.load(fileInputStream, tsPwd);
      }
      tmf.init(ts);

      kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      final KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
      try (final FileInputStream fileInputStream = new FileInputStream(ksLocation)) {
        ks.load(fileInputStream, ksPwd);
      }
      kmf.init(ks, ksPwd);
      if (log.isInfoEnabled()) {
        logKeyStore(ks, ksLocation, ksPwd);
      }
    } catch (final FileNotFoundException e) {
      throw new AtomixRuntimeException("Could not load cluster keystore: %s", e.getMessage());
    } catch (final Exception e) {
      throw new AtomixRuntimeException("Error loading cluster keystore", e);
    }
    this.trustManager = tmf;
    this.keyManager = kmf;
    return true;
  }

  private void logKeyStore(final KeyStore ks, final String ksLocation, final char[] ksPwd) {
    if (log.isInfoEnabled()) {
      log.info("Loaded cluster key store from: {}", ksLocation);
      try {
        for (final Enumeration<String> e = ks.aliases(); e.hasMoreElements(); ) {
          final String alias = e.nextElement();
          final Key key = ks.getKey(alias, ksPwd);
          final Certificate[] certs = ks.getCertificateChain(alias);
          log.debug("{} -> {}", alias, certs);
          final byte[] encodedKey;
          if (certs != null && certs.length > 0) {
            encodedKey = certs[0].getEncoded();
          } else {
            log.info(
                "Could not find cert chain for {}, using fingerprint of key instead...", alias);
            encodedKey = key.getEncoded();
          }
          // Compute the certificate's fingerprint (use the key if certificate cannot be found)
          final MessageDigest digest = MessageDigest.getInstance("SHA1");
          digest.update(encodedKey);
          final StringJoiner fingerprint = new StringJoiner(":");
          for (final byte b : digest.digest()) {
            fingerprint.add(String.format("%02X", b));
          }
          log.info("{} -> {}", alias, fingerprint);
        }
      } catch (final Exception e) {
        log.warn("Unable to print contents of key store: {}", ksLocation, e);
      }
    }
  }

  private void initEventLoopGroup() {
    // try Epoll first and if that does work, use nio.
    try {
      clientGroup =
          new EpollEventLoopGroup(0, namedThreads("netty-messaging-event-epoll-client-%d", log));
      serverGroup =
          new EpollEventLoopGroup(0, namedThreads("netty-messaging-event-epoll-server-%d", log));
      serverChannelClass = EpollServerSocketChannel.class;
      clientChannelClass = EpollSocketChannel.class;
      return;
    } catch (final Throwable e) {
      log.debug(
          "Failed to initialize native (epoll) transport. " + "Reason: {}. Proceeding with nio.",
          e.getMessage());
    }
    clientGroup =
        new NioEventLoopGroup(0, namedThreads("netty-messaging-event-nio-client-%d", log));
    serverGroup =
        new NioEventLoopGroup(0, namedThreads("netty-messaging-event-nio-server-%d", log));
    serverChannelClass = NioServerSocketChannel.class;
    clientChannelClass = NioSocketChannel.class;
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
    final CompletableFuture<T> future = new CompletableFuture<T>();
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
   * @param future the future to be completed once the callback future is complete
   * @param <T> the callback response type
   */
  private <T> void executeOnPooledConnection(
      final Address address,
      final String type,
      final Function<ClientConnection, CompletableFuture<T>> callback,
      final Executor executor,
      final CompletableFuture<T> future) {
    if (address.equals(returnAddress)) {
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
      return;
    }

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
                            executor.execute(() -> future.complete(result));
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
                            executor.execute(() -> future.completeExceptionally(sendError));
                          }
                        });
              } else {
                executor.execute(() -> future.completeExceptionally(channelError));
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
    if (address.equals(returnAddress)) {
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
          connections.computeIfAbsent(channel, c -> new RemoteClientConnection(timeoutExecutor, c));
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
    final InetAddress resolvedAddress = address.address(true);
    if (resolvedAddress == null) {
      future.completeExceptionally(
          new IllegalStateException(
              "Failed to bootstrap client (address "
                  + address.toString()
                  + " cannot be resolved)"));
      return future;
    }

    final Bootstrap bootstrap = new Bootstrap();
    bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
    bootstrap.option(
        ChannelOption.WRITE_BUFFER_WATER_MARK,
        new WriteBufferWaterMark(10 * 32 * 1024, 10 * 64 * 1024));
    bootstrap.option(ChannelOption.SO_RCVBUF, 1024 * 1024);
    bootstrap.option(ChannelOption.SO_SNDBUF, 1024 * 1024);
    bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
    bootstrap.option(ChannelOption.TCP_NODELAY, true);
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
    bootstrap.group(clientGroup);
    // TODO: Make this faster:
    // http://normanmaurer.me/presentations/2014-facebook-eng-netty/slides.html#37.0
    bootstrap.channel(clientChannelClass);
    bootstrap.remoteAddress(resolvedAddress, address.port());
    if (enableNettyTls) {
      try {
        bootstrap.handler(new SslClientChannelInitializer(future, address));
      } catch (final SSLException e) {
        return Futures.exceptionalFuture(e);
      }
    } else {
      bootstrap.handler(new BasicClientChannelInitializer(future));
    }
    bootstrap
        .connect()
        .addListener(
            f -> {
              if (!f.isSuccess()) {
                future.completeExceptionally(f.cause());
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
    b.childOption(ChannelOption.SO_RCVBUF, 1024 * 1024);
    b.childOption(ChannelOption.SO_SNDBUF, 1024 * 1024);
    b.childOption(ChannelOption.SO_KEEPALIVE, true);
    b.childOption(ChannelOption.TCP_NODELAY, true);
    b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
    b.group(serverGroup, clientGroup);
    b.channel(serverChannelClass);
    if (enableNettyTls) {
      try {
        b.childHandler(new SslServerChannelInitializer());
      } catch (final SSLException e) {
        return Futures.exceptionalFuture(e);
      }
    } else {
      b.childHandler(new BasicServerChannelInitializer());
    }
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
    final int port = config.getPort() != null ? config.getPort() : returnAddress.port();
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
      final ServerBootstrap bootstrap,
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
                      log.info("TCP server listening for connections on {}:{}", iface, port);
                      serverChannel = f.channel();
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

  /** Channel initializer for TLS clients. */
  private class SslClientChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final CompletableFuture<Channel> future;
    private final Address address;
    private final SslContext sslContext;

    SslClientChannelInitializer(final CompletableFuture<Channel> future, final Address address)
        throws SSLException {
      this.future = future;
      this.address = address;
      this.sslContext =
          SslContextBuilder.forClient().keyManager(keyManager).trustManager(trustManager).build();
    }

    @Override
    protected void initChannel(final SocketChannel channel) throws Exception {
      channel
          .pipeline()
          .addLast("ssl", sslContext.newHandler(channel.alloc(), address.host(), address.port()))
          .addLast("handshake", new ClientHandshakeHandlerAdapter(future));
    }
  }

  /** Channel initializer for TLS servers. */
  private final class SslServerChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final SslContext sslContext;

    private SslServerChannelInitializer() throws SSLException {
      this.sslContext =
          SslContextBuilder.forServer(keyManager)
              .clientAuth(ClientAuth.REQUIRE)
              .trustManager(trustManager)
              .build();
    }

    @Override
    protected void initChannel(final SocketChannel channel) throws Exception {
      channel
          .pipeline()
          .addLast("ssl", sslContext.newHandler(channel.alloc()))
          .addLast("handshake", new ServerHandshakeHandlerAdapter());
    }
  }

  /** Channel initializer for basic connections. */
  private class BasicClientChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final CompletableFuture<Channel> future;

    BasicClientChannelInitializer(final CompletableFuture<Channel> future) {
      this.future = future;
    }

    @Override
    protected void initChannel(final SocketChannel channel) throws Exception {
      channel.pipeline().addLast("handshake", new ClientHandshakeHandlerAdapter(future));
    }
  }

  /** Channel initializer for basic connections. */
  private class BasicServerChannelInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(final SocketChannel channel) throws Exception {
      channel.pipeline().addLast("handshake", new ServerHandshakeHandlerAdapter());
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
        final ProtocolVersion protocolVersion) {
      final MessagingProtocol protocol = protocolVersion.createProtocol(returnAddress);
      context.pipeline().remove(this);
      context.pipeline().addLast("encoder", protocol.newEncoder());
      context.pipeline().addLast("decoder", protocol.newDecoder());
      context.pipeline().addLast("handler", new MessageDispatcher<>(connection));
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
                      context, getOrCreateClientConnection(context.channel()), protocolVersion);
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
        final ProtocolVersion protocolVersion) {
      log.debug(
          "Activating client protocol version {} for connection to {}",
          protocolVersion,
          context.channel().remoteAddress());
      super.activateProtocolVersion(context, connection, protocolVersion);
      future.complete(context.channel());
    }
  }

  /** Server handshake handler. */
  private class ServerHandshakeHandlerAdapter extends HandshakeHandlerAdapter<ProtocolRequest> {
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
                    protocolVersion);
              });
    }

    @Override
    void activateProtocolVersion(
        final ChannelHandlerContext context,
        final Connection<ProtocolRequest> connection,
        final ProtocolVersion protocolVersion) {
      log.debug(
          "Activating server protocol version {} for connection to {}",
          protocolVersion,
          context.channel().remoteAddress());
      super.activateProtocolVersion(context, connection, protocolVersion);
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
      }
    }
  }
}
