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
package io.zeebe.transport.util;

import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.TransportListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingChannelListener implements TransportListener {

  protected List<RemoteAddress> closedConnections = new CopyOnWriteArrayList<>();
  protected List<RemoteAddress> openedConnections = new CopyOnWriteArrayList<>();
  protected List<Event> events = new CopyOnWriteArrayList<>();

  public List<RemoteAddress> getClosedConnections() {
    return closedConnections;
  }

  public List<RemoteAddress> getOpenedConnections() {
    return openedConnections;
  }

  public List<Event> getEvents() {
    return events;
  }

  @Override
  public void onConnectionEstablished(RemoteAddress remoteAddress) {
    events.add(Event.ESTABLISHED);
    openedConnections.add(remoteAddress);
  }

  @Override
  public void onConnectionClosed(RemoteAddress remoteAddress) {
    events.add(Event.CLOSED);
    closedConnections.add(remoteAddress);
  }

  public enum Event {
    ESTABLISHED,
    CLOSED
  }
}
