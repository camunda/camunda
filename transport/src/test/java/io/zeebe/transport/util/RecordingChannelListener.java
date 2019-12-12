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

public final class RecordingChannelListener implements TransportListener {

  protected final List<RemoteAddress> closedConnections = new CopyOnWriteArrayList<>();
  protected final List<RemoteAddress> openedConnections = new CopyOnWriteArrayList<>();
  protected final List<Event> events = new CopyOnWriteArrayList<>();

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
  public void onConnectionEstablished(final RemoteAddress remoteAddress) {
    events.add(Event.ESTABLISHED);
    openedConnections.add(remoteAddress);
  }

  @Override
  public void onConnectionClosed(final RemoteAddress remoteAddress) {
    events.add(Event.CLOSED);
    closedConnections.add(remoteAddress);
  }

  public enum Event {
    ESTABLISHED,
    CLOSED
  }
}
