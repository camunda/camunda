/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.zeebe.transport.impl.AtomixClientTransportAdapter;
import io.zeebe.transport.impl.AtomixServerTransport;
import io.zeebe.util.sched.ActorScheduler;

public final class TransportFactory {

  // we need to schedule the transports, but Actor is not an interface
  // which means we need to schedule in the factory otherwise we can return the transport interface
  // types
  private final ActorScheduler actorScheduler;

  public TransportFactory(final ActorScheduler actorScheduler) {
    this.actorScheduler = actorScheduler;
  }

  public ServerTransport createServerTransport(
      final int nodeId, final ClusterCommunicationService clusterCommunicationService) {
    final var atomixServerTransport =
        new AtomixServerTransport(nodeId, clusterCommunicationService);
    actorScheduler.submitActor(atomixServerTransport);
    return atomixServerTransport;
  }

  public ClientTransport createClientTransport(
      final ClusterCommunicationService clusterCommunicationService) {
    final var atomixClientTransportAdapter =
        new AtomixClientTransportAdapter(clusterCommunicationService);
    actorScheduler.submitActor(atomixClientTransportAdapter);
    return atomixClientTransportAdapter;
  }
}
