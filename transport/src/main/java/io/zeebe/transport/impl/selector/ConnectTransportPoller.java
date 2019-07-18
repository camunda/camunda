/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.selector;

import io.zeebe.transport.impl.TransportChannel;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.agrona.nio.TransportPoller;

public class ConnectTransportPoller extends TransportPoller {
  protected final List<TransportChannel> channelsToAdd = new ArrayList<>();
  protected final List<TransportChannel> channelsToRemove = new ArrayList<>();
  protected final ToIntFunction<SelectionKey> processKeyFn = this::processKey;

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

    if (selector.isOpen()) {
      for (TransportChannel channel : channelsToAdd) {
        channel.registerSelector(selector, SelectionKey.OP_CONNECT);
      }

      for (TransportChannel channel : channelsToRemove) {
        channel.removeSelector(selector);
      }
    }
    channelsToAdd.clear();
    channelsToRemove.clear();
  }

  protected int processKey(SelectionKey key) {
    if (key != null && key.isValid()) {
      final TransportChannel channel = (TransportChannel) key.attachment();
      removeChannel(channel);
      channel.finishConnect();
      return 1;
    }

    return 0;
  }

  public void addChannel(TransportChannel channel) {
    channelsToAdd.add(channel);
    selector.wakeup();
  }

  public void removeChannel(TransportChannel channel) {
    channelsToRemove.add(channel);
    selector.wakeup();
  }
}
