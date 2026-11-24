/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.mtls;

import io.grpc.netty.GrpcHttp2ConnectionHandler;
import io.grpc.netty.InternalProtocolNegotiator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AsciiString;
import java.util.function.Supplier;

/**
 * Wraps grpc-java's server TLS negotiator to add a {@link HandshakeObserver} to every connection,
 * so every handshake outcome is reported together with the channel's remote address; the trust
 * manager alone sees neither handshakes failing before it is called (no certificate) nor the peer
 * address.
 *
 * <p>Relies on grpc-java's internal negotiator API ({@code InternalProtocolNegotiator}), which may
 * change between grpc-java releases.
 */
public final class ObservedServerTlsNegotiator
    implements InternalProtocolNegotiator.ProtocolNegotiator {

  private final InternalProtocolNegotiator.ProtocolNegotiator delegate;
  private final Supplier<ChannelHandler> observerFactory;

  public ObservedServerTlsNegotiator(
      final InternalProtocolNegotiator.ProtocolNegotiator delegate,
      final Supplier<ChannelHandler> observerFactory) {
    this.delegate = delegate;
    this.observerFactory = observerFactory;
  }

  @Override
  public AsciiString scheme() {
    return delegate.scheme();
  }

  @Override
  public ChannelHandler newHandler(final GrpcHttp2ConnectionHandler grpcHandler) {
    final var tlsHandler = delegate.newHandler(grpcHandler);
    return new ChannelInboundHandlerAdapter() {
      @Override
      public void handlerAdded(final ChannelHandlerContext ctx) {
        // grpc-java only adds its SslHandler once the channel is active, at a position we do not
        // control, so the observer attaches to the SslHandler's handshake future instead
        ctx.pipeline().replace(this, "zeebe-tls-negotiation", tlsHandler);
        ctx.pipeline().addLast("zeebe-tls-observer", observerFactory.get());
      }
    };
  }

  @Override
  public void close() {
    delegate.close();
  }
}
