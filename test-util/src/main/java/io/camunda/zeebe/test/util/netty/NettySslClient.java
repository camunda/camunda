/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.netty;

import io.camunda.zeebe.util.exception.RecoverableException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.Future;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.agrona.LangUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple Netty based client which only connects to SSL/TLS secured connections and extracts their
 * certificates.
 *
 * <p>While it can take any {@link SslContext}, it's most likely that for testing you will use it in
 * combination with a {@link SelfSignedCertificate}, via the factory method {@link
 * NettySslClient#ofSelfSigned(SelfSignedCertificate)}.
 *
 * <p>If you simply want to assert whether or not an endpoint is secured with a given certificate,
 * look at {@link io.camunda.zeebe.test.util.asserts.SslAssert}. This custom AssertJ assertion
 * reuses this class to perform its assertions.
 */
public final class NettySslClient {
  private static final int MAX_ATTEMPT_COUNT = 3;

  private static final Logger LOGGER = LoggerFactory.getLogger(NettySslClient.class);
  private final SslContext sslContext;

  public NettySslClient(final SslContext sslContext) {
    this.sslContext = sslContext;
  }

  /**
   * Returns a client which implicitly trusts the given {@link SelfSignedCertificate}.
   *
   * @param certificate the certificate to trust
   * @return a new gullible client
   */
  public static NettySslClient ofSelfSigned(final SelfSignedCertificate certificate) {
    try {
      return new NettySslClient(
          SslContextBuilder.forClient().trustManager(certificate.certificate()).build());
    } catch (final SSLException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Extracts the certificate chain used by the remote peer at {@code address} after performing a
   * successful SSL/TLS handshake.
   *
   * <p>The operation will fail if it fails to connect within 10 seconds, or if the address is
   * otherwise unreachable. Similarly, it will fail if the SSL handshake fails.
   *
   * @param address the remote address to connect to
   * @return the certificate chain used by the remote server
   */
  public Certificate[] getRemoteCertificateChain(final SocketAddress address) {
    return getRemoteCertificateChain(address, 1);
  }

  /**
   * It can happen that the channel is closed too early due to network hiccups, in which case we
   * want to retry a few times (assuming the channel was closed implicitly from the client side). If
   * it fails 3 times, it's very likely to be a real error.
   */
  private Certificate[] getRemoteCertificateChain(
      final SocketAddress address, final int attemptCount) {
    if (attemptCount > MAX_ATTEMPT_COUNT) {
      throw new IllegalStateException(
          "Failed to obtain remote certificate chain after retrying 3 times; see logs for more");
    }

    final var certificatesFuture = new CompletableFuture<Certificate[]>();
    final var executor = new NioEventLoopGroup(1);

    try {
      final var connectFuture =
          new Bootstrap()
              .handler(new SslCertificateExtractor(certificatesFuture))
              .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
              // prevent implicitly the channel on write errors so we can deal with errors in an
              // explicit fashion
              .option(ChannelOption.AUTO_CLOSE, false)
              .group(executor)
              .channel(NioSocketChannel.class)
              .connect(address)
              .addListener(onConnect -> onChannelConnect(address, certificatesFuture, onConnect));

      final var channel = connectFuture.channel();
      channel
          .closeFuture()
          .addListener(onClose -> onChannelClose(address, certificatesFuture, onClose));

      try {
        return certificatesFuture.orTimeout(10, TimeUnit.SECONDS).join();
      } catch (final CompletionException e) {
        if (e.getCause() instanceof PrematurelyClosedChannelException) {
          LOGGER.debug(
              "Retrying prematurely closed connection to {}, attempt #{} in {} seconds",
              address,
              attemptCount,
              attemptCount);
          LockSupport.parkNanos(Duration.ofSeconds(attemptCount).toNanos());
          return getRemoteCertificateChain(address, attemptCount + 1);
        }

        // rethrow otherwise
        throw e;
      }
    } finally {
      closeExecutor(executor);
    }
  }

  private static void closeExecutor(final EventLoopGroup executor) {
    try {
      // will close any opened channel
      executor.shutdownGracefully(10, 100, TimeUnit.MILLISECONDS).sync();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LangUtil.rethrowUnchecked(e);
    }
  }

  private void onChannelConnect(
      final SocketAddress address,
      final CompletableFuture<Certificate[]> certificates,
      final Future<? super Void> onConnect) {
    if (onConnect.isSuccess()) {
      return;
    }

    final var errorMessage =
        String.format("Failed to establish a secure connection to %s", address);
    certificates.completeExceptionally(getErrorWithOptionalCause(onConnect, errorMessage));
  }

  private void onChannelClose(
      final SocketAddress address,
      final CompletableFuture<Certificate[]> certificates,
      final Future<? super Void> onClose) {
    if (onClose.cause() == null || (onClose.cause() instanceof ClosedChannelException)) {
      certificates.completeExceptionally(new PrematurelyClosedChannelException(onClose.cause()));
    } else {
      final var errorMessage =
          String.format(
              "Channel to remote peer %s was unexpectedly closed before any certificates were "
                  + "extracted",
              address);
      certificates.completeExceptionally(getErrorWithOptionalCause(onClose, errorMessage));
    }
  }

  private Throwable getErrorWithOptionalCause(
      final Future<?> operation, final String errorMessage) {
    final Throwable error;
    if (operation.cause() != null) {
      error = new IllegalStateException(errorMessage, operation.cause());
    } else {
      error = new IllegalStateException(errorMessage);
    }
    return error;
  }

  private static final class PrematurelyClosedChannelException extends RecoverableException {

    public PrematurelyClosedChannelException(final Throwable cause) {
      super(cause);
    }
  }

  private final class SslCertificateExtractor extends ChannelInitializer<SocketChannel> {
    private final CompletableFuture<Certificate[]> extractedCertificate;

    public SslCertificateExtractor(final CompletableFuture<Certificate[]> extractedCertificate) {
      this.extractedCertificate = extractedCertificate;
    }

    @Override
    protected void initChannel(final SocketChannel channel) {
      final var sslHandler = sslContext.newHandler(channel.alloc());
      sslHandler
          .handshakeFuture()
          .addListener(onHandshake -> extractCertificate(sslHandler, onHandshake));
      sslHandler.sslCloseFuture().addListener(this::onSslClose);
      sslHandler.setHandshakeTimeoutMillis(5_000);

      channel.pipeline().addLast("tls", sslHandler);
      channel
          .pipeline()
          .addLast(
              new LoggingHandler(LogLevel.TRACE) {
                @Override
                public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause)
                    throws Exception {
                  extractedCertificate.completeExceptionally(cause);
                  super.exceptionCaught(ctx, cause);
                }
              });
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause)
        throws Exception {
      extractedCertificate.completeExceptionally(cause);
      super.exceptionCaught(ctx, cause);
    }

    private void onSslClose(final Future<? super Channel> onSslClose) {
      if (!onSslClose.isSuccess()) {
        extractedCertificate.completeExceptionally(
            getErrorWithOptionalCause(
                onSslClose, "SSL engine closed before certificates were extracted"));
      }
    }

    private void extractCertificate(
        final SslHandler sslHandler, final Future<? super Channel> onHandshake)
        throws SSLPeerUnverifiedException {
      if (onHandshake.isSuccess()) {
        extractedCertificate.complete(sslHandler.engine().getSession().getPeerCertificates());
      } else {
        final var error = getErrorWithOptionalCause(onHandshake, "Failed to perform SSL handshake");
        extractedCertificate.completeExceptionally(error);

        // log it as well as sometimes the channel is closed (and the future completed) before the
        // handshake future is failed, so the cause would be lost otherwise
        LOGGER.debug(
            "Could not verify the SSL certificate, error occurred during handshake", error);
      }
    }
  }
}
