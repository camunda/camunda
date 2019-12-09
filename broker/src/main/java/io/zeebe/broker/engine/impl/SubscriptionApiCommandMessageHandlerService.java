/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine.impl;

import static io.zeebe.broker.Broker.actorNamePattern;

import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.PartitionListener;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandMessageHandler;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.util.sched.Actor;
import org.agrona.collections.Int2ObjectHashMap;

public class SubscriptionApiCommandMessageHandlerService extends Actor
    implements PartitionListener {

  private final Int2ObjectHashMap<LogStreamRecordWriter> leaderPartitions =
      new Int2ObjectHashMap<>();
  private final Atomix atomix;
  private final BrokerInfo localBroker;

  public SubscriptionApiCommandMessageHandlerService(BrokerInfo localBroker, Atomix atomix) {
    this.localBroker = localBroker;
    this.atomix = atomix;
  }

  @Override
  public String getName() {
    return actorNamePattern(localBroker, "SubscriptionApi");
  }

  @Override
  public void onBecomingFollower(int partitionId) {
    actor.submit(() -> leaderPartitions.remove(partitionId));
  }

  @Override
  public void onBecomingLeader(int partitionId, LogStream logStream) {
    actor.submit(
        () -> {
          logStream
              .newLogStreamRecordWriter()
              .onComplete(
                  (recordWriter, error) -> {
                    if (error == null) {
                      leaderPartitions.put(partitionId, recordWriter);
                    } else {
                      // TODO https://github.com/zeebe-io/zeebe/issues/3499
                      Loggers.SYSTEM_LOGGER.error(
                          "Unexpected error on retrieving write buffer for partition {}",
                          partitionId,
                          error);
                    }
                  });
        });
  }

  @Override
  protected void onActorStarting() {
    final SubscriptionCommandMessageHandler messageHandler =
        new SubscriptionCommandMessageHandler(actor::call, leaderPartitions::get);
    atomix.getCommunicationService().subscribe("subscription", messageHandler);
  }
}
