/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog;

import io.atomix.primitive.SyncPrimitive;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface DistributedLogstream extends SyncPrimitive {

  long append(
      String partition, String nodeId, long appendIndex, long commitPosition, byte[] blockBuffer)
      throws InterruptedException, ExecutionException, TimeoutException;

  long lastAppendIndex(String partition)
      throws InterruptedException, ExecutionException, TimeoutException;

  @Override
  AsyncDistributedLogstream async();
}
