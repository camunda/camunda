/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import java.util.ArrayList;
import java.util.List;

final class TestClientStreamMetrics implements ClientStreamMetrics {

  private final List<Integer> aggregatedClientCountObservations = new ArrayList<>();

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
}
