/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine.impl;

import io.atomix.core.Atomix;
import io.zeebe.broker.PartitionListener;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandMessageHandler;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.util.sched.Actor;
import org.agrona.collections.Int2ObjectHashMap;

public class SubscriptionApiCommandMessageHandlerService extends Actor
    implements PartitionListener {

  private final Int2ObjectHashMap<LogStream> leaderPartitions = new Int2ObjectHashMap<>();
  private final Atomix atomix;

  public SubscriptionApiCommandMessageHandlerService(Atomix atomix) {
    this.atomix = atomix;
  }

  @Override
  public String getName() {
    return "subscription-api";
  }

  @Override
  public void onBecomingFollower(int partitionId) {
    actor.submit(() -> leaderPartitions.remove(partitionId));
  }

  @Override
  public void onBecomingLeader(int partitionId, LogStream logStream) {
    actor.submit(() -> leaderPartitions.put(partitionId, logStream));
  }

  @Override
  protected void onActorStarting() {
    SubscriptionCommandMessageHandler messageHandler =
        new SubscriptionCommandMessageHandler(actor::call, leaderPartitions::get);
    atomix.getCommunicationService().subscribe("subscription", messageHandler);
  }
}
