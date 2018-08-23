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
package io.zeebe.gossip.dissemination;

import io.zeebe.gossip.GossipCustomEventListener;
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.protocol.CustomEvent;
import io.zeebe.gossip.protocol.CustomEventConsumer;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class CustomEventListenerConsumer implements CustomEventConsumer {
  private static final Logger LOG = Loggers.GOSSIP_LOGGER;

  private final List<Tuple<DirectBuffer, GossipCustomEventListener>> listenersByType =
      new ArrayList<>();

  @Override
  public boolean consumeCustomEvent(CustomEvent event) {
    for (Tuple<DirectBuffer, GossipCustomEventListener> tuple : listenersByType) {
      if (BufferUtil.equals(tuple.getLeft(), event.getType())) {
        final GossipCustomEventListener listener = tuple.getRight();

        try {
          listener.onEvent(event.getSenderId(), event.getPayload());
        } catch (Throwable t) {
          LOG.warn("Custom event listener '{}' failed", listener.getClass(), t);
        }
      }
    }

    return true;
  }

  public void addCustomEventListener(DirectBuffer eventType, GossipCustomEventListener listener) {
    listenersByType.add(new Tuple<>(eventType, listener));
  }

  public void removeCustomEventListener(GossipCustomEventListener listener) {
    listenersByType.removeIf(tuple -> tuple.getRight() == listener);
  }
}
