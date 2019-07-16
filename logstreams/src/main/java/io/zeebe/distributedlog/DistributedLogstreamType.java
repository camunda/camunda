/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog;

import io.atomix.primitive.PrimitiveManagementService;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.service.PrimitiveService;
import io.atomix.primitive.service.ServiceConfig;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamBuilder;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamService;
import io.zeebe.distributedlog.impl.DistributedLogstreamConfig;

public class DistributedLogstreamType
    implements PrimitiveType<
        DistributedLogstreamBuilder, DistributedLogstreamConfig, DistributedLogstream> {

  public static PrimitiveType instance() {

    return new DistributedLogstreamType();
  }

  @Override
  public String name() {
    return "Distributed-logstream";
  }

  @Override
  public DistributedLogstreamConfig newConfig() {
    return new DistributedLogstreamConfig();
  }

  @Override
  public DistributedLogstreamBuilder newBuilder(
      String s,
      DistributedLogstreamConfig distributedLogstreamConfig,
      PrimitiveManagementService primitiveManagementService) {
    return new DefaultDistributedLogstreamBuilder(
        s, distributedLogstreamConfig, primitiveManagementService);
  }

  @Override
  public PrimitiveService newService(ServiceConfig serviceConfig) {
    return new DefaultDistributedLogstreamService();
  }
}
