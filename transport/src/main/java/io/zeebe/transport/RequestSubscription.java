/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface RequestSubscription {

  /**
   * Subscribes to the given partition and call's the given handler on each new request.
   *
   * @param partitionId the partition, for which should be subscribed
   * @param requestHandler the handler which should be called.
   */
  void subscribe(int partitionId, Function<byte[], CompletableFuture<byte[]>> requestHandler);

  /**
   * Unsubscribe from the given partition, the registered handler will no longer be called on new
   * requests.
   *
   * @param partitionId the partition, from which we should unsubscribe
   */
  void unsubscribe(int partitionId);
}
