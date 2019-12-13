/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.zeebe.transport.RequestSubscription;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class AtomixRequestSubscription implements RequestSubscription {

  private static final String API_TOPIC_FORMAT = "command-api-%d";

  private final ClusterCommunicationService communicationService;

  public AtomixRequestSubscription(final ClusterCommunicationService communicationService) {
    this.communicationService = communicationService;
  }

  @Override
  public void subscribe(
      final int partitionId, final Function<byte[], CompletableFuture<byte[]>> requestHandler) {
    communicationService.subscribe(topicName(partitionId), requestHandler);
  }

  @Override
  public void unsubscribe(final int partitionId) {
    communicationService.unsubscribe(topicName(partitionId));
  }

  public static String topicName(final int partitionId) {
    return String.format(API_TOPIC_FORMAT, partitionId);
  }
}
