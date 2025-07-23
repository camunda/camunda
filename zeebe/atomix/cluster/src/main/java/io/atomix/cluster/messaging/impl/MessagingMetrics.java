/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import io.camunda.zeebe.util.CloseableSilently;
import io.netty.util.AttributeKey;

public interface MessagingMetrics {
  AttributeKey<String> CHANNEL_ID_ATTRIBUTE = AttributeKey.newInstance("io.camunda.channel.id");

  CloseableSilently startRequestTimer(String name);

  void observeRequestSize(String to, String name, int requestSizeInBytes);

  void countMessage(String to, String name, String channelId);

  void countRequestResponse(String to, String name, String channelId);

  void countSuccessResponse(String address, String name);

  void countFailureResponse(String address, String name, String error);

  void incInFlightRequests(String address, String topic);

  void decInFlightRequests(String address, String topic);
}
