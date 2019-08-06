/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log.impl;

import io.zeebe.distributedlog.restore.log.LogReplicationRequest;

public class DefaultLogReplicationRequest implements LogReplicationRequest {
  private long fromPosition;
  private long toPosition;
  private boolean includeFromPosition;

  public DefaultLogReplicationRequest() {}

  public DefaultLogReplicationRequest(long fromPosition, long toPosition) {
    this(fromPosition, toPosition, false);
  }

  public DefaultLogReplicationRequest(
      long fromPosition, long toPosition, boolean includeFromPosition) {
    this.fromPosition = fromPosition;
    this.toPosition = toPosition;
    this.includeFromPosition = includeFromPosition;
  }

  @Override
  public boolean includeFromPosition() {
    return includeFromPosition;
  }

  @Override
  public long getFromPosition() {
    return fromPosition;
  }

  public void setFromPosition(long fromPosition) {
    this.fromPosition = fromPosition;
  }

  @Override
  public long getToPosition() {
    return toPosition;
  }

  public void setToPosition(long toPosition) {
    this.toPosition = toPosition;
  }

  public void setIncludeFromPosition(boolean includeFromPosition) {
    this.includeFromPosition = includeFromPosition;
  }

  @Override
  public String toString() {
    return "DefaultLogReplicationRequest{"
        + "fromPosition="
        + fromPosition
        + ", toPosition="
        + toPosition
        + ", includeFromPosition="
        + includeFromPosition
        + '}';
  }
}
