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
