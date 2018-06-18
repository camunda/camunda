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

import io.zeebe.transport.Loggers;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.util.sched.ActorControl;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.agrona.LangUtil;
import org.agrona.nio.TransportPoller;
import org.slf4j.Logger;

public class ReadTransportPoller extends TransportPoller {
  private final ActorControl actor;

  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  protected final List<TransportChannel> channels = new ArrayList<>();
  protected final List<TransportChannel> channelsToAdd = new ArrayList<>();

  protected final ToIntFunction<SelectionKey> processKeyFn = this::processKey;
  protected final Runnable pollNow = this::pollNow;

  public ReadTransportPoller(ActorControl actor) {
    this.actor = actor;
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

  public void pollBlockingEnded(Throwable t) {
    maintainChannels();
    processKeys();
    actor.runUntilDone(pollNow);
  }

  public void pollNow() {
    int workCount = 0;

    if (channels.size() <= ITERATION_THRESHOLD) {
      for (int i = 0; i < channels.size(); i++) {
        final TransportChannel channel = channels.get(i);
        workCount += channel.receive();
      }
    } else {
      try {
        selector.selectNow();
        workCount += processKeys();
      } catch (IOException e) {
        selectedKeySet.reset();
        LangUtil.rethrowUnchecked(e);
      }
    }

    if (workCount == 0) {
      actor.done();
      actor.runBlocking(this::pollBlocking, this::pollBlockingEnded);
    } else {
      actor.yield();
    }
  }

  private void maintainChannels() {
    for (int i = 0; i < channelsToAdd.size(); i++) {
      final TransportChannel channel = channelsToAdd.get(i);
      try {
        channel.registerSelector(selector, SelectionKey.OP_READ);
        channels.add(channel);
      } catch (Exception e) {
        LOG.debug("Failed to add channel {}", channel, e);
      }
    }
    channelsToAdd.clear();
  }

  public int processKeys() {
    return selectedKeySet.forEach(processKeyFn);
  }

  protected int processKey(SelectionKey key) {
    int workCount = 0;

    if (key != null && key.isValid()) {
      final TransportChannel channel = (TransportChannel) key.attachment();
      workCount = channel.receive();
    }

    return workCount;
  }

  public void addChannel(TransportChannel channel) {
    channelsToAdd.add(channel);
    selector.wakeup();
  }

  public void removeChannel(TransportChannel channel) {
    channels.remove(channel);
  }

  public void clearChannels() {
    channels.clear();
  }
}
