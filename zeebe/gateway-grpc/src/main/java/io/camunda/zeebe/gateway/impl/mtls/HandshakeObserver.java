/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.mtls;

import io.camunda.zeebe.util.tls.PeerCertificateRejectedException;
import io.camunda.zeebe.util.tls.PeerCertificateTrustManager;
import io.camunda.zeebe.util.tls.PeerIdentity;
import io.camunda.zeebe.util.tls.PeerVerificationListener.RejectionReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import java.security.cert.X509Certificate;
import java.util.Locale;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.slf4j.Logger;

/**
 * Logs and counts the outcome of one TLS handshake, with the peer's address: which client
 * certificate was accepted, or why the handshake failed. Removes itself once attached to the
 * connection's {@link SslHandler}.
 *
 * <p>Rejections are tagged by reason, acceptances by the allow-list entry that matched, so tag
 * cardinality stays bounded by configuration; presented identities only go into the log.
 */
public final class HandshakeObserver extends ChannelInboundHandlerAdapter {
  static final String NO_CERTIFICATE_IDENTITY = "none";

  private final PeerCertificateTrustManager trustManager;
  private final MeterRegistry registry;
  private final String metricPrefix;
  private final Logger logger;

  /**
   * @param metricPrefix e.g. {@code zeebe.gateway.grpc.client.certificate}
   */
  public HandshakeObserver(
      final PeerCertificateTrustManager trustManager,
      final MeterRegistry registry,
      final String metricPrefix,
      final Logger logger) {
    this.trustManager = trustManager;
    this.registry = registry;
    this.metricPrefix = metricPrefix;
    this.logger = logger;
  }

  @Override
  public void handlerAdded(final ChannelHandlerContext ctx) {
    tryAttach(ctx);
  }

  @Override
  public void channelActive(final ChannelHandlerContext ctx) throws Exception {
    // the SslHandler may only be added once the channel is active, by a handler before this one
    tryAttach(ctx);
    super.channelActive(ctx);
  }

  private void tryAttach(final ChannelHandlerContext ctx) {
    final var sslHandler = ctx.pipeline().get(SslHandler.class);
    if (sslHandler == null || ctx.isRemoved()) {
      return;
    }

    final var peer = String.valueOf(ctx.channel().remoteAddress());
    sslHandler
        .handshakeFuture()
        .addListener(
            handshake -> {
              if (handshake.isSuccess()) {
                onSuccess(sslHandler, peer);
              } else {
                onFailure(handshake.cause(), peer);
              }
            });
    ctx.pipeline().remove(this);
  }

  private void onSuccess(final SslHandler sslHandler, final String peer) {
    final var session = sslHandler.engine().getSession();
    final X509Certificate leaf;
    try {
      leaf = (X509Certificate) session.getPeerCertificates()[0];
    } catch (final SSLPeerUnverifiedException e) {
      logger.debug("Accepted connection from {} without client certificate", peer);
      countAccepted(NO_CERTIFICATE_IDENTITY);
      return;
    }

    final var identity = PeerCertificateTrustManager.identityOf(leaf, peer);
    final var matched = trustManager.matchingIdentity(identity).orElse("?");
    logger.debug(
        "Accepted client certificate {} matching allowed identity '{}'", identity, matched);
    countAccepted(matched);
  }

  private void onFailure(final Throwable cause, final String peer) {
    final var rejection = PeerCertificateRejectedException.find(cause);
    if (rejection != null) {
      final var presented = rejection.identity();
      final var identity =
          new PeerIdentity(
              presented.subject(),
              presented.subjectAlternativeNames(),
              presented.commonName(),
              peer);
      logger.warn(
          "Rejected client certificate {}: {} ({})",
          identity,
          rejection.reason(),
          rejection.detail());
      countRejected(rejection.reason());
    } else if (isMissingCertificate(cause)) {
      logger.warn("Rejected connection from {}: no client certificate presented", peer);
      countRejected(RejectionReason.NO_CERTIFICATE);
    } else {
      logger.warn("TLS handshake with {} failed: {}", peer, cause.toString());
      countRejected(RejectionReason.HANDSHAKE_FAILED);
    }
  }

  /**
   * Neither the JDK nor BoringSSL expose a typed error for a missing client certificate; match on
   * the messages they use.
   */
  private static boolean isMissingCertificate(final Throwable cause) {
    for (Throwable t = cause; t != null; t = t.getCause()) {
      final var message = String.valueOf(t.getMessage()).toUpperCase(Locale.ROOT);
      if (message.contains("PEER_DID_NOT_RETURN_A_CERTIFICATE")
          || message.contains("CERTIFICATE_REQUIRED")
          || message.contains("EMPTY CLIENT CERTIFICATE CHAIN")) {
        return true;
      }
    }
    return false;
  }

  private void countAccepted(final String identity) {
    Counter.builder(metricPrefix + ".accepted")
        .description("TLS handshakes which passed client verification, by matched allowed identity")
        .tag("identity", identity)
        .register(registry)
        .increment();
  }

  private void countRejected(final RejectionReason reason) {
    Counter.builder(metricPrefix + ".rejected")
        .description("TLS handshakes which failed, by reason")
        .tag("reason", reason.name())
        .register(registry)
        .increment();
  }
}
