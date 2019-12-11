/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.util;

import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.transport.impl.RemoteAddressListImpl;
import io.zeebe.transport.impl.TransportChannel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ControllableServerTransport implements AutoCloseable {
  protected final Map<SocketAddress, ServerSocketChannel> serverChannels = new HashMap<>();
  protected final Map<SocketAddress, List<TransportChannel>> clientChannels = new HashMap<>();
  protected final RemoteAddressListImpl remoteList = new RemoteAddressListImpl();

  public ControllableServerTransport() {
    remoteList.setOnAddressAddedConsumer(
        (addr) -> {
          // no-op
        });
  }

  public void listenOn(final SocketAddress localAddress) {
    ServerSocketChannel socketChannel = null;
    try {
      socketChannel = ServerSocketChannel.open();
      socketChannel.bind(localAddress.toInetSocketAddress());
      socketChannel.configureBlocking(true);
      serverChannels.put(localAddress, socketChannel);
    } catch (final IOException e1) {
      if (socketChannel != null) {
        try {
          socketChannel.close();
        } catch (final IOException e2) {
          throw new RuntimeException(e2);
        }
      }
      throw new RuntimeException(e1);
    }
  }

  public AtomicInteger acceptNextConnection(final SocketAddress localAddress) {
    final AtomicInteger messageCounter = new AtomicInteger(0);
    acceptNextConnection(localAddress, messageCounter);
    return messageCounter;
  }

  public List<TransportChannel> getClientChannels(final SocketAddress localAddress) {
    return clientChannels.get(localAddress);
  }

  public void acceptNextConnection(
      final SocketAddress localAddress, final AtomicInteger messageCounter) {
    try {
      final SocketChannel clientChannel = serverChannels.get(localAddress).accept();
      clientChannel.configureBlocking(false);
      final RemoteAddressImpl remoteAddress =
          remoteList.register(
              new SocketAddress((InetSocketAddress) clientChannel.getRemoteAddress()));

      final TransportChannel c =
          new TransportChannel(
              null,
              remoteAddress,
              128,
              (b, o, l, streamId, failed) -> {
                messageCounter.incrementAndGet();
                return FragmentHandler.CONSUME_FRAGMENT_RESULT;
              },
              clientChannel);

      clientChannels.computeIfAbsent(localAddress, a -> new ArrayList<>());
      clientChannels.get(localAddress).add(c);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public int receive(final SocketAddress localAddress) {
    return clientChannels.get(localAddress).stream().mapToInt(c -> c.receive()).sum();
  }

  @Override
  public void close() {
    clientChannels.forEach((a, channels) -> channels.forEach(c -> c.close()));

    for (final ServerSocketChannel channel : serverChannels.values()) {
      try {
        channel.close();
      } catch (final IOException e) {
        e.printStackTrace();
      }
    }
  }
}
