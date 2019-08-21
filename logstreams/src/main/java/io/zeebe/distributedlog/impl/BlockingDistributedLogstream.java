/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.impl;

import io.atomix.primitive.Synchronous;
import io.zeebe.distributedlog.AsyncDistributedLogstream;
import io.zeebe.distributedlog.DistributedLogstream;
import io.zeebe.logstreams.impl.Loggers;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;

public class BlockingDistributedLogstream extends Synchronous<AsyncDistributedLogstream>
    implements DistributedLogstream {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;
  private final DistributedLogstreamProxy distributedLogstreamProxy;
  private final long timeout;

  public BlockingDistributedLogstream(
      DistributedLogstreamProxy distributedLogstreamProxy, long timeout) {
    super(distributedLogstreamProxy);
    this.distributedLogstreamProxy = distributedLogstreamProxy;
    this.timeout = timeout;
  }

  @Override
  public long append(String partition, String nodeId, long commitPosition, byte[] blockBuffer) {
    try {
      return distributedLogstreamProxy
          .append(partition, nodeId, commitPosition, blockBuffer)
          .get(timeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      LOG.error("Unexpected exception appeared during blocking append.", e);
    }
    // Append failed
    return -1;
  }

  @Override
  public void claimLeaderShip(String partition, String nodeId, long leaderTerm) {
    try {
      distributedLogstreamProxy
          .claimLeaderShip(partition, nodeId, leaderTerm)
          .get(timeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      LOG.error("Unexpected exception appeared during blocking claim leadership.", e);
    }
  }

  @Override
  public AsyncDistributedLogstream async() {
    return distributedLogstreamProxy;
  }
}
