/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;
import java.util.EnumMap;
import java.util.Map;

final class TestRemoteStreamMetrics implements RemoteStreamMetrics {

  private int streamCount;
  private int pushSucceeded;
  private int pushFailed;
  private final Map<ErrorCode, Integer> failedPushTries = new EnumMap<>(ErrorCode.class);

  @Override
  public void addStream() {
    streamCount++;
  }

  @Override
  public void removeStream() {
    streamCount--;
  }

  @Override
  public void pushSucceeded() {
    pushSucceeded++;
  }

  @Override
  public void pushFailed() {
    pushFailed++;
  }

  @Override
  public void pushTryFailed(final ErrorCode code) {
    failedPushTries.compute(code, (ignored, value) -> value == null ? 1 : value + 1);
  }

  public int getStreamCount() {
    return streamCount;
  }

  public int getPushSucceeded() {
    return pushSucceeded;
  }

  public int getPushFailed() {
    return pushFailed;
  }

  public int getFailedPushTry(final ErrorCode code) {
    return failedPushTries.getOrDefault(code, 0);
  }
}
