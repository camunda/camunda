/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport;

import io.atomix.cluster.messaging.MessagingService;
import io.camunda.zeebe.transport.impl.AtomixClientTransportAdapter;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.sched.ActorSchedulingService;

public final class TransportFactory {

  private final ActorSchedulingService actorSchedulingService;

  public TransportFactory(final ActorSchedulingService actorSchedulingService) {
    this.actorSchedulingService = actorSchedulingService;
  }

  public ServerTransport createServerTransport(
      final int nodeId, final MessagingService messagingService) {
    final var atomixServerTransport = new AtomixServerTransport(nodeId, messagingService);
    actorSchedulingService.submitActor(atomixServerTransport);
    return atomixServerTransport;
  }

  public ClientTransport createClientTransport(final MessagingService messagingService) {
    final var atomixClientTransportAdapter = new AtomixClientTransportAdapter(messagingService);
    actorSchedulingService.submitActor(atomixClientTransportAdapter);
    return atomixClientTransportAdapter;
  }
}
