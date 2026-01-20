/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport;

import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.scheduler.future.ActorFuture;

public interface ServerTransport extends ServerOutput, AutoCloseable {

  /**
   * Subscribes to the given partition and call's the given handler on each new request of the given
   * type.
   *
   * @param partitionId the partition, for which should be subscribed
   * @param requestType the type of request that should be handled
   * @param requestHandler the handler which should be called.
   */
  ActorFuture<Void> subscribe(
      PartitionId partitionId, RequestType requestType, RequestHandler requestHandler);

  default ActorFuture<Void> subscribe(
      final int partitionId, final RequestType requestType, final RequestHandler requestHandler) {
    return subscribe(new PartitionId("raft-partition", partitionId), requestType, requestHandler);
  }

  /**
   * Unsubscribe from the given partition, the registered handler will no longer be called on new
   * requests.
   *
   * @param partitionId the partition, from which we should unsubscribe
   * @param requestType
   */
  ActorFuture<Void> unsubscribe(PartitionId partitionId, RequestType requestType);

  default ActorFuture<Void> unsubscribe(final int partitionId, final RequestType requestType) {
    return unsubscribe(new PartitionId("raft-partition", partitionId), requestType);
  }
}
