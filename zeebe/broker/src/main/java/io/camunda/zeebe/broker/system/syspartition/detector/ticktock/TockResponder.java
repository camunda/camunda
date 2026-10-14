/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import java.util.function.Consumer;

/**
 * Acknowledges each tick this broker applied to the leader which put it, the leader included: it is
 * seen only once it applied its own tick, like every other broker. A lost tock is not retried: the
 * next tick produces the next one.
 */
final class TockResponder {
  private final ClusterCommunicationService communicationService;
  private final String localMemberId;
  private final Consumer<Tock> localLeader;

  /**
   * @param localLeader receives the tocks of ticks this broker put itself, without a network hop
   */
  TockResponder(
      final ClusterCommunicationService communicationService,
      final String localMemberId,
      final Consumer<Tock> localLeader) {
    this.communicationService = communicationService;
    this.localMemberId = localMemberId;
    this.localLeader = localLeader;
  }

  void onTick(final long tickId, final String leaderId) {
    final var tock = new Tock(localMemberId, tickId);
    if (leaderId.equals(localMemberId)) {
      localLeader.accept(tock);
      return;
    }
    communicationService.unicast(Tock.SUBJECT, tock, Tock::encode, MemberId.from(leaderId), true);
  }
}
