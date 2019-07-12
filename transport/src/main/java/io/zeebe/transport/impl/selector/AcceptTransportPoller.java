/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.selector;

import io.zeebe.transport.impl.ServerSocketBinding;
import io.zeebe.transport.impl.actor.ServerConductor;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.ToIntFunction;
import org.agrona.nio.TransportPoller;

public class AcceptTransportPoller extends TransportPoller {
  private final ServerConductor serverConductor;
  private final ToIntFunction<SelectionKey> processKeyFn = this::processKey;

  public AcceptTransportPoller(ServerConductor serverConductor) {
    this.serverConductor = serverConductor;
  }

  public void pollBlocking() {
    if (selector.isOpen()) {
      try {
        selector.select();
      } catch (IOException e) {
        selectedKeySet.reset();
        throw new RuntimeException(e);
      }
    }
  }

  public void processKeys() {
    selectedKeySet.forEach(processKeyFn);
  }

  protected int processKey(SelectionKey key) {
    if (key != null && key.isValid()) {
      final ServerSocketBinding serverSocketBinding = (ServerSocketBinding) key.attachment();
      final SocketChannel serverChannel = serverSocketBinding.accept();

      serverConductor.onServerChannelOpened(serverChannel);

      return 1;
    }

    return 0;
  }

  public void addServerSocketBinding(ServerSocketBinding binding) {
    binding.registerSelector(selector, SelectionKey.OP_ACCEPT);
  }
}
