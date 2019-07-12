/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog;

import io.atomix.primitive.SyncPrimitive;

public interface DistributedLogstream extends SyncPrimitive {

  long append(String partition, String nodeId, long commitPosition, byte[] blockBuffer);

  void claimLeaderShip(String partition, String nodeId, long leaderTerm);

  @Override
  AsyncDistributedLogstream async();
}
