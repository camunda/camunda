/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class TestClientStreamMetrics implements ClientStreamMetrics {

  private final List<Integer> aggregatedClientCountObservations = new ArrayList<>();
  private final Map<ErrorCode, Integer> failedPushTries = new EnumMap<>(ErrorCode.class);

  private int serverCount;
  private int clientCount;
  private int aggregatedStreamCount;
  private int pushSucceeded;
  private int pushFailed;

  @Override
  public void serverCount(final int count) {
    serverCount = count;
  }

  @Override
  public void clientCount(final int count) {
    clientCount = count;
  }

  @Override
  public void aggregatedStreamCount(final int count) {
    aggregatedStreamCount = count;
  }

  @Override
  public void observeAggregatedClientCount(final int count) {
    aggregatedClientCountObservations.add(count);
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

  public int getServerCount() {
    return serverCount;
  }

  public int getClientCount() {
    return clientCount;
  }

  public int getAggregatedStreamCount() {
    return aggregatedStreamCount;
  }

  public List<Integer> getAggregatedClientCountObservations() {
    return aggregatedClientCountObservations;
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
