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

import io.zeebe.transport.impl.TransportChannel;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.agrona.nio.TransportPoller;

public class ConnectTransportPoller extends TransportPoller {
  protected final ToIntFunction<SelectionKey> processKeyFn = this::processKey;

  protected final List<TransportChannel> channelsToAdd = new ArrayList<>();
  protected final List<TransportChannel> channelsToRemove = new ArrayList<>();

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
