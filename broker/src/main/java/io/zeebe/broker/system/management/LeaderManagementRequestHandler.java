/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.management;

import static io.zeebe.broker.Broker.actorNamePattern;

import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.util.sched.Actor;
import org.agrona.collections.Int2ObjectHashMap;

public class LeaderManagementRequestHandler extends Actor implements PartitionListener {

  private final Int2ObjectHashMap<LogStreamRecordWriter> leaderForPartitions =
      new Int2ObjectHashMap<>();
  private final BrokerInfo localBroker;
  private PushDeploymentRequestHandler pushDeploymentRequestHandler;
  private final Atomix atomix;

  public LeaderManagementRequestHandler(BrokerInfo localBroker, Atomix atomix) {
    this.localBroker = localBroker;
    this.atomix = atomix;
  }

  @Override
  public void onBecomingFollower(final int partitionId) {
    actor.submit(() -> leaderForPartitions.remove(partitionId));
  }

  @Override
  public void onBecomingLeader(final int partitionId, final LogStream logStream) {
    actor.submit(
        () ->
            logStream
                .newLogStreamRecordWriter()
                .onComplete(
                    (recordWriter, error) -> {
                      if (error == null) {
                        leaderForPartitions.put(partitionId, recordWriter);
                      } else {
                        Loggers.CLUSTERING_LOGGER.error(
                            "Unexpected error on retrieving write buffer for partition {}",
                            partitionId,
                            error);
                        // TODO https://github.com/zeebe-io/zeebe/issues/3499
                      }
                    }));
  }

  @Override
  public String getName() {
    return actorNamePattern(localBroker, "ManagementRequestHandler");
  }

  @Override
  protected void onActorStarting() {
    pushDeploymentRequestHandler =
        new PushDeploymentRequestHandler(leaderForPartitions, actor, atomix);
    atomix.getCommunicationService().subscribe("deployment", pushDeploymentRequestHandler);
  }

  public PushDeploymentRequestHandler getPushDeploymentRequestHandler() {
    return pushDeploymentRequestHandler;
  }
}
