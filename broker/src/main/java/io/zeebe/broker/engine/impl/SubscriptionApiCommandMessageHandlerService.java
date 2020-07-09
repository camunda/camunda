/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine.impl;

import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandMessageHandler;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.CompletableFuture;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

public final class SubscriptionApiCommandMessageHandlerService extends Actor
    implements PartitionListener, DiskSpaceUsageListener {

  private static final String SUBSCRIPTION_TOPIC = "subscription";
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private final Int2ObjectHashMap<LogStreamRecordWriter> leaderPartitions =
      new Int2ObjectHashMap<>();
  private final Atomix atomix;
  private final String actorName;
  private SubscriptionCommandMessageHandler messageHandler;

  public SubscriptionApiCommandMessageHandlerService(
      final BrokerInfo localBroker, final Atomix atomix) {
    this.atomix = atomix;
    this.actorName = buildActorName(localBroker.getNodeId(), "SubscriptionApi");
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarting() {
    messageHandler = new SubscriptionCommandMessageHandler(actor::call, leaderPartitions::get);
    atomix.getCommunicationService().subscribe(SUBSCRIPTION_TOPIC, messageHandler);
  }

  @Override
  public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
    return actor.call(
        () -> {
          leaderPartitions.remove(partitionId);
          return null;
        });
  }

  @Override
  public ActorFuture<Void> onBecomingLeader(
      final int partitionId, final long term, final LogStream logStream) {
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    actor.submit(
        () ->
            logStream
                .newLogStreamRecordWriter()
                .onComplete(
                    (recordWriter, error) -> {
                      if (error == null) {
                        leaderPartitions.put(partitionId, recordWriter);
                        future.complete(null);
                      } else {
                        LOG.error(
                            "Unexpected error on retrieving write buffer for partition {}",
                            partitionId,
                            error);
                        future.completeExceptionally(error);
                      }
                    }));
    return future;
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.call(
        () -> {
          LOG.debug(
              "Broker is out of disk space. All requests with topic {} will be rejected.",
              SUBSCRIPTION_TOPIC);
          atomix.getCommunicationService().unsubscribe(SUBSCRIPTION_TOPIC);
          atomix
              .getCommunicationService()
              // SubscriptionMessageHandler does not send any response
              .subscribe(SUBSCRIPTION_TOPIC, b -> CompletableFuture.completedFuture(null));
        });
  }

  @Override
  public void onDiskSpaceAvailable() {
    actor.call(
        () -> {
          LOG.debug(
              "Broker has disk space available again. All requests with topic {} will be accepted.",
              SUBSCRIPTION_TOPIC);
          atomix.getCommunicationService().unsubscribe(SUBSCRIPTION_TOPIC);
          atomix.getCommunicationService().subscribe(SUBSCRIPTION_TOPIC, messageHandler);
        });
  }
}
