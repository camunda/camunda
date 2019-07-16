/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;

public class RemoteAddressImpl implements RemoteAddress {
  public static final int STATE_ACTIVE = 1 << 0;
  public static final int STATE_INACTIVE = 1 << 1;
  public static final int STATE_RETIRED = 1 << 2;

  private final int streamId;
  private final SocketAddress addr;
  private volatile int state;

  public RemoteAddressImpl(int streamId, SocketAddress addr) {
    this.streamId = streamId;
    this.addr = addr;
    this.state = STATE_ACTIVE;
  }

  public int getStreamId() {
    return streamId;
  }

  public SocketAddress getAddress() {
    return addr;
  }

  public void deactivate() {
    this.state = STATE_INACTIVE;
  }

  public void retire() {
    this.state = STATE_RETIRED;
  }

  public void activate() {
    this.state = STATE_ACTIVE;
  }

  public boolean isInAnyState(int mask) {
    return (this.state & mask) != 0;
  }

  public boolean isActive() {
    return isInAnyState(STATE_ACTIVE);
  }

  @Override
  public String toString() {
    return "RemoteAddress{" + "streamId=" + streamId + ", addr=" + addr + '}';
  }
}
