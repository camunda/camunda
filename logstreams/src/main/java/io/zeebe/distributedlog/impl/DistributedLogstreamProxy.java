/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.impl;

import io.atomix.primitive.AbstractAsyncPrimitive;
import io.atomix.primitive.PrimitiveRegistry;
import io.atomix.primitive.proxy.ProxyClient;
import io.atomix.primitive.proxy.ProxySession;
import io.atomix.utils.concurrent.Futures;
import io.zeebe.distributedlog.AsyncDistributedLogstream;
import io.zeebe.distributedlog.DistributedLogstream;
import io.zeebe.distributedlog.DistributedLogstreamClient;
import io.zeebe.distributedlog.DistributedLogstreamService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedLogstreamProxy
    extends AbstractAsyncPrimitive<AsyncDistributedLogstream, DistributedLogstreamService>
    implements AsyncDistributedLogstream, DistributedLogstreamClient {

  private static final Logger LOG = LoggerFactory.getLogger(DistributedLogstreamProxy.class);
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(1000);

  protected DistributedLogstreamProxy(
      ProxyClient<DistributedLogstreamService> client, PrimitiveRegistry registry) {
    super(client, registry);
  }

  @Override
  public CompletableFuture<Long> append(
      String partition, String nodeId, long commitPosition, byte[] buffer) {
    return getProxyClient()
        .applyBy(partition, service -> service.append(nodeId, commitPosition, buffer));
  }

  @Override
  public CompletableFuture<Boolean> claimLeaderShip(
      String partition, String nodeId, long leaderTerm) {
    return getProxyClient()
        .applyBy(partition, service -> service.claimLeaderShip(nodeId, leaderTerm));
  }

  @Override
  public DistributedLogstream sync() {
    return sync(DEFAULT_TIMEOUT);
  }

  @Override
  public DistributedLogstream sync(Duration duration) {
    return new BlockingDistributedLogstream(this, duration.toMillis());
  }

  @Override
  public CompletableFuture<AsyncDistributedLogstream> connect() {
    return super.connect()
        .thenCompose(
            v ->
                Futures.allOf(
                    this.getProxyClient().getPartitions().stream().map(ProxySession::connect)))
        .thenApply(v -> this);
  }
}
