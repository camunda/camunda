/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.management;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.CompletableFuture;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

public final class LeaderManagementRequestHandler extends Actor
    implements PartitionListener, DiskSpaceUsageListener {

  private static final String DEPLOYMENT_TOPIC = "deployment";
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;
  private final Int2ObjectHashMap<LogStreamRecordWriter> leaderForPartitions =
      new Int2ObjectHashMap<>();
  private final String actorName;
  private PushDeploymentRequestHandler pushDeploymentRequestHandler;
  private final ErrorResponse outOfDiskSpaceError;
  private final ClusterCommunicationService communicationService;
  private final ClusterEventService eventService;

  public LeaderManagementRequestHandler(
      final BrokerInfo localBroker,
      final ClusterCommunicationService communicationService,
      final ClusterEventService eventService) {
    this.communicationService = communicationService;
    this.eventService = eventService;
    actorName = buildActorName(localBroker.getNodeId(), "ManagementRequestHandler");
    outOfDiskSpaceError = new ErrorResponse();
    outOfDiskSpaceError
        .setErrorCode(ErrorCode.RESOURCE_EXHAUSTED)
        .setErrorData(
            BufferUtil.wrapString(
                String.format(
                    "Broker %d is out of disk space. Rejecting deployment request.",
                    localBroker.getNodeId())));
  }

  @Override
  public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
    return actor.call(
        () -> {
          leaderForPartitions.remove(partitionId);
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
                        leaderForPartitions.put(partitionId, recordWriter);
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
          leaderForPartitions.remove(partitionId);
          return null;
        });
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarting() {
    pushDeploymentRequestHandler =
        new PushDeploymentRequestHandler(leaderForPartitions, actor, eventService);
    communicationService.subscribe(DEPLOYMENT_TOPIC, pushDeploymentRequestHandler);
  }

  public PushDeploymentRequestHandler getPushDeploymentRequestHandler() {
    return pushDeploymentRequestHandler;
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.call(
        () -> {
          LOG.debug(
              "Broker is out of disk space. All requests with topic {} will be rejected.",
              DEPLOYMENT_TOPIC);
          communicationService.unsubscribe(DEPLOYMENT_TOPIC);
          communicationService.subscribe(
              DEPLOYMENT_TOPIC,
              b -> CompletableFuture.completedFuture(outOfDiskSpaceError.toBytes()));
        });
  }

  @Override
  public void onDiskSpaceAvailable() {
    actor.call(
        () -> {
          LOG.debug(
              "Broker has disk space available again. All requests with topic {} will be accepted.",
              DEPLOYMENT_TOPIC);
          communicationService.unsubscribe(DEPLOYMENT_TOPIC);
          communicationService.subscribe(DEPLOYMENT_TOPIC, pushDeploymentRequestHandler);
        });
  }
}
