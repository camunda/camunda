/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.Future;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.security.cert.Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;

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
    final var certificatesFuture = new CompletableFuture<Certificate[]>();
    final var executor = new NioEventLoopGroup(1);

    try {
      final var channel =
          new Bootstrap()
              .handler(new SslCertificateExtractor(certificatesFuture))
              .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
              .group(executor)
              .channel(NioSocketChannel.class)
              .connect(address)
              .addListener(onConnect -> onChannelConnect(address, certificatesFuture, onConnect))
              .channel();
      channel
          .closeFuture()
          .addListener(onClose -> onChannelClose(address, certificatesFuture, onClose));

      final Certificate[] certificateChain =
          certificatesFuture.orTimeout(10, TimeUnit.SECONDS).join();
      channel.close();

      return certificateChain;
    } finally {
      executor.shutdownGracefully(10, 100, TimeUnit.MILLISECONDS);
    }
  }

  private void onChannelConnect(
      final SocketAddress address,
      final CompletableFuture<Certificate[]> certificates,
      final Future<? super Void> onConnect) {
    if (!onConnect.isSuccess()) {
      final var errorMessage =
          String.format("Failed to establish a secure connection to %s", address);
      certificates.completeExceptionally(getErrorWithOptionalCause(onConnect, errorMessage));
    }
  }

  private void onChannelClose(
      final SocketAddress address,
      final CompletableFuture<Certificate[]> certificates,
      final Future<? super Void> onClose) {
    final var errorMessage =
        String.format(
            "Channel to remote peer %s was unexpectedly closed before any certificates were "
                + "extracted",
            address);
    certificates.completeExceptionally(getErrorWithOptionalCause(onClose, errorMessage));
  }

  private Throwable getErrorWithOptionalCause(
      final Future<? super Void> onClose, final String errorMessage) {
    final Throwable error;
    if (onClose.cause() != null) {
      error = new IllegalStateException(errorMessage, onClose.cause());
    } else {
      error = new IllegalStateException(errorMessage);
    }
    return error;
  }

  private final class SslCertificateExtractor extends ChannelInitializer<SocketChannel> {
    private final CompletableFuture<Certificate[]> extractedCertificate;

    public SslCertificateExtractor(final CompletableFuture<Certificate[]> extractedCertificate) {
      this.extractedCertificate = extractedCertificate;
    }

    @Override
    protected void initChannel(final SocketChannel channel) {
      final var sslHandler = sslContext.newHandler(channel.alloc());
      channel.pipeline().addLast("tls", sslHandler);
      sslHandler
          .handshakeFuture()
          .addListener(onHandshake -> extractCertificate(sslHandler, onHandshake));
    }

    private void extractCertificate(
        final SslHandler sslHandler, final Future<? super Channel> onHandshake)
        throws SSLPeerUnverifiedException {
      if (onHandshake.isSuccess()) {
        extractedCertificate.complete(sslHandler.engine().getSession().getPeerCertificates());
      }
    }
  }
}
