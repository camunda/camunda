/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.management;

import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.protocol.impl.encoding.ErrorResponse;
import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
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
  private final Atomix atomix;
  private final ErrorResponse outOfDiskSpaceError;

  public LeaderManagementRequestHandler(final BrokerInfo localBroker, final Atomix atomix) {
    this.atomix = atomix;
    this.actorName = buildActorName(localBroker.getNodeId(), "ManagementRequestHandler");
    this.outOfDiskSpaceError = new ErrorResponse();
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
      final int partitionId, final long term, final LogStream logStream) {
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
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarting() {
    pushDeploymentRequestHandler =
        new PushDeploymentRequestHandler(leaderForPartitions, actor, atomix);
    atomix.getCommunicationService().subscribe(DEPLOYMENT_TOPIC, pushDeploymentRequestHandler);
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
          atomix.getCommunicationService().unsubscribe(DEPLOYMENT_TOPIC);
          atomix
              .getCommunicationService()
              .subscribe(
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
          atomix.getCommunicationService().unsubscribe(DEPLOYMENT_TOPIC);
          atomix
              .getCommunicationService()
              .subscribe(DEPLOYMENT_TOPIC, pushDeploymentRequestHandler);
        });
  }
}
