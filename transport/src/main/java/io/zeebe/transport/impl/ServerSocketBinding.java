/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.transport.impl;

import io.zeebe.transport.Loggers;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;

public class ServerSocketBinding {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  protected final List<Selector> registeredSelectors =
      Collections.synchronizedList(new ArrayList<>());
  protected final InetSocketAddress bindAddress;

  protected ServerSocketChannel media;

  public ServerSocketBinding(final InetSocketAddress bindAddress) {
    this.bindAddress = bindAddress;
  }

  public void doBind() {
    try {
      media = ServerSocketChannel.open();
      media.bind(bindAddress);
      media.configureBlocking(false);
    } catch (IOException e) {
      throw new RuntimeException("Failed to bind to address: " + bindAddress, e);
    }
  }

  public void registerSelector(Selector selector, int op) {
    try {
      final SelectionKey key = media.register(selector, op);
      key.attach(this);
      registeredSelectors.add(selector);
    } catch (ClosedChannelException e) {
      throw new RuntimeException(e);
    }
  }

  public void removeSelector(Selector selector) {
    final SelectionKey key = media.keyFor(selector);
    if (key != null) {
      key.cancel();

      try {
        // required to reuse socket on windows, see https://github.com/kaazing/nuklei/issues/20
        selector.select(1);
      } catch (IOException e) {
        LOG.debug("Failed to remove selector {}", selector, e);
      }
    }
  }

  public SocketChannel accept() {
    try {
      final SocketChannel socketChannel = media.accept();
      socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
      socketChannel.configureBlocking(false);
      return socketChannel;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void close() {
    try {
      synchronized (registeredSelectors) {
        registeredSelectors.forEach(s -> removeSelector(s));
      }
    } catch (Exception e) {
      LOG.debug("Failed to close selectors", e);
    }
    releaseMedia();
  }

  public void releaseMedia() {
    try {
      media.close();
    } catch (IOException e) {
      LOG.debug("Failed to close media", e);
    }
  }
}
