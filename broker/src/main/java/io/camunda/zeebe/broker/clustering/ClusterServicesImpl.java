/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.clustering;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.MessagingService;
import java.util.concurrent.CompletableFuture;

public class ClusterServicesImpl implements ClusterServices {

  private final AtomixCluster atomixCluster;

  public ClusterServicesImpl(final AtomixCluster atomixCluster) {
    this.atomixCluster = atomixCluster;
  }

  public CompletableFuture<Void> start() {
    return atomixCluster.start();
  }

  public CompletableFuture<Void> stop() {
    return atomixCluster.stop();
  }

  @Override
  public MessagingService getMessagingService() {
    return atomixCluster.getMessagingService();
  }

  @Override
  public ClusterMembershipService getMembershipService() {
    return atomixCluster.getMembershipService();
  }

  @Override
  public ClusterEventService getEventService() {
    return atomixCluster.getEventService();
  }

  @Override
  public ClusterCommunicationService getCommunicationService() {
    return atomixCluster.getCommunicationService();
  }

  public AtomixCluster getAtomixCluster() {
    return atomixCluster;
  }
}
