/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine.impl;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandMessageHandler;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.CompletableFuture;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

public final class SubscriptionApiCommandMessageHandlerService extends Actor
    implements PartitionListener, DiskSpaceUsageListener {

  private static final String SUBSCRIPTION_TOPIC = "subscription";
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private final Int2ObjectHashMap<LogStreamRecordWriter> leaderPartitions =
      new Int2ObjectHashMap<>();
  private final ClusterCommunicationService communicationService;
  private final String actorName;
  private SubscriptionCommandMessageHandler messageHandler;

  public SubscriptionApiCommandMessageHandlerService(
      final BrokerInfo localBroker, final ClusterCommunicationService communicationService) {
    this.communicationService = communicationService;
    actorName = buildActorName(localBroker.getNodeId(), "SubscriptionApi");
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarting() {
    messageHandler = new SubscriptionCommandMessageHandler(actor::call, leaderPartitions::get);
    communicationService.subscribe(SUBSCRIPTION_TOPIC, messageHandler);
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
      final int partitionId,
      final long term,
      final LogStream logStream,
      final QueryService queryService) {
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
  public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
    return actor.call(
        () -> {
          leaderPartitions.remove(partitionId);
          return null;
        });
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.call(
        () -> {
          LOG.debug(
              "Broker is out of disk space. All requests with topic {} will be rejected.",
              SUBSCRIPTION_TOPIC);
          communicationService.unsubscribe(SUBSCRIPTION_TOPIC);
          communicationService
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
          communicationService.unsubscribe(SUBSCRIPTION_TOPIC);
          communicationService.subscribe(SUBSCRIPTION_TOPIC, messageHandler);
        });
  }
}
