/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.actor;

import io.zeebe.transport.TransportListener;
import io.zeebe.transport.impl.sender.Sender;
import io.zeebe.util.sched.future.ActorFuture;

public abstract class ActorContext {
  private Conductor conductor;
  private Sender sender;
  private Receiver receiver;

  public void removeListener(TransportListener listener) {
    conductor.removeListener(listener);
  }

  public ActorFuture<Void> registerListener(TransportListener channelListener) {
    return conductor.registerListener(channelListener);
  }

  public ActorFuture<Void> onClose() {
    return conductor.closeAsync();
  }

  public ActorFuture<Void> closeAllOpenChannels() {
    return conductor.closeCurrentChannels();
  }

  public ActorFuture<Void> interruptAllChannels() {
    return conductor.interruptAllChannels();
  }

  public ActorFuture<Void> closeReceiver() {
    return receiver.closeAsync();
  }

  public Conductor getConductor() {
    return conductor;
  }

  public void setConductor(Conductor clientConductor) {
    this.conductor = clientConductor;
  }

  public ClientConductor getClientConductor() {
    return (ClientConductor) conductor;
  }

  public ServerConductor getServerConductor() {
    return (ServerConductor) conductor;
  }

  public Receiver getReceiver() {
    return receiver;
  }

  public void setReceiver(Receiver receiver) {
    this.receiver = receiver;
  }

  public Sender getSender() {
    return sender;
  }

  public void setSender(Sender sender) {
    this.sender = sender;
  }

  public ActorFuture<Void> closeSender() {
    return sender.closeAsync();
  }
}
