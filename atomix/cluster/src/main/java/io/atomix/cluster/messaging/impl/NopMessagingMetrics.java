/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging.impl;

import io.camunda.zeebe.util.CloseableSilently;

public class NopMessagingMetrics implements MessagingMetrics {

  @Override
  public CloseableSilently startRequestTimer(final String name) {
    return () -> {};
  }

  @Override
  public void observeRequestSize(
      final String to, final String name, final int requestSizeInBytes) {}

  @Override
  public void countMessage(final String to, final String name) {}

  @Override
  public void countRequestResponse(final String to, final String name) {}

  @Override
  public void countSuccessResponse(final String address, final String name) {}

  @Override
  public void countFailureResponse(final String address, final String name, final String error) {}

  @Override
  public void incInFlightRequests(final String address, final String topic) {}

  @Override
  public void decInFlightRequests(final String address, final String topic) {}
}
