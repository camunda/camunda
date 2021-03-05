/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.transport;

import io.zeebe.util.sched.future.ActorFuture;

public interface ServerTransport extends ServerOutput, AutoCloseable {

  /**
   * Subscribes to the given partition and call's the given handler on each new request.
   *
   * @param partitionId the partition, for which should be subscribed
   * @param requestHandler the handler which should be called.
   */
  ActorFuture<Void> subscribe(int partitionId, RequestHandler requestHandler);

  /**
   * Unsubscribe from the given partition, the registered handler will no longer be called on new
   * requests.
   *
   * @param partitionId the partition, from which we should unsubscribe
   */
  ActorFuture<Void> unsubscribe(int partitionId);
}
