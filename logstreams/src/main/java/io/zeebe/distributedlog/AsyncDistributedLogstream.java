/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog;

import io.atomix.primitive.AsyncPrimitive;
import java.util.concurrent.CompletableFuture;

public interface AsyncDistributedLogstream extends AsyncPrimitive {

  CompletableFuture<Long> append(
      String partition, String nodeId, long commitPosition, byte[] blockBuffer);

  CompletableFuture<Boolean> claimLeaderShip(String partition, String nodeId, long leaderTerm);

  @Override
  DistributedLogstream sync();
}
