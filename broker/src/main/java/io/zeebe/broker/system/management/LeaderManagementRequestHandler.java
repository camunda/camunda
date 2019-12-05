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
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.util.sched.Actor;
import org.agrona.collections.Int2ObjectHashMap;

public class LeaderManagementRequestHandler extends Actor implements PartitionListener {

  private final Int2ObjectHashMap<LogStreamRecordWriter> leaderForPartitions = new Int2ObjectHashMap<>();
  private PushDeploymentRequestHandler pushDeploymentRequestHandler;
  private final Atomix atomix;

  public LeaderManagementRequestHandler(Atomix atomix) {
    this.atomix = atomix;
  }

  @Override
  public void onBecomingFollower(final int partitionId) {
    actor.submit(() -> leaderForPartitions.remove(partitionId));
  }

  @Override
  public void onBecomingLeader(final int partitionId, final LogStream logStream) {
    actor.submit(() -> logStream.getWriteBufferAsync().onComplete((writeBuffer, error) ->
      {
        if (error == null)
        {
          final LogStreamRecordWriter logStreamWriter = new LogStreamWriterImpl(writeBuffer,
            partitionId);
          leaderForPartitions.put(partitionId, logStreamWriter);
        }
        else
        {
          Loggers.CLUSTERING_LOGGER.error("Unexpected error on retrieving write buffer for partition {}", partitionId, error);
          // todo ideally we can fail on becoming leader future and step down
        }
      }));
  }

  @Override
  public String getName() {
    return "management-request-handler";
  }

  @Override
  protected void onActorStarting() {
    Loggers.SYSTEM_LOGGER.error("Starting handler");
    pushDeploymentRequestHandler =
        new PushDeploymentRequestHandler(leaderForPartitions, actor, atomix);
    atomix.getCommunicationService().subscribe("deployment", pushDeploymentRequestHandler);
  }

  public PushDeploymentRequestHandler getPushDeploymentRequestHandler() {
    return pushDeploymentRequestHandler;
  }
}
