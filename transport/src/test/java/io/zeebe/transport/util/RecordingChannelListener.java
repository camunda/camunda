/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
