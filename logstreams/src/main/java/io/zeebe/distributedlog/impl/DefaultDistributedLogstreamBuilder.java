/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.impl;

import io.atomix.primitive.PrimitiveManagementService;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.protocol.ProxyProtocol;
import io.atomix.primitive.service.ServiceConfig;
import io.zeebe.distributedlog.AsyncDistributedLogstream;
import io.zeebe.distributedlog.DistributedLogstream;
import io.zeebe.distributedlog.DistributedLogstreamBuilder;
import io.zeebe.distributedlog.DistributedLogstreamService;
import java.util.concurrent.CompletableFuture;

public class DefaultDistributedLogstreamBuilder extends DistributedLogstreamBuilder {

  public DefaultDistributedLogstreamBuilder(
      String name,
      DistributedLogstreamConfig config,
      PrimitiveManagementService managementService) {
    super(name, config, managementService);
  }

  @Override
  public CompletableFuture<DistributedLogstream> buildAsync() {
    return newProxy(DistributedLogstreamService.class, new ServiceConfig())
        .thenCompose(
            proxyClient ->
                new DistributedLogstreamProxy(proxyClient, managementService.getPrimitiveRegistry())
                    .connect())
        .thenApply(AsyncDistributedLogstream::sync);
  }

  @Override
  public DistributedLogstreamBuilder withProtocol(ProxyProtocol proxyProtocol) {
    return this.withProtocol((PrimitiveProtocol) proxyProtocol);
  }
}
