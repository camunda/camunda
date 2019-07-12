/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog;

import io.atomix.primitive.PrimitiveBuilder;
import io.atomix.primitive.PrimitiveManagementService;
import io.atomix.primitive.protocol.ProxyCompatibleBuilder;
import io.zeebe.distributedlog.impl.DistributedLogstreamConfig;

public abstract class DistributedLogstreamBuilder
    extends PrimitiveBuilder<
        DistributedLogstreamBuilder, DistributedLogstreamConfig, DistributedLogstream>
    implements ProxyCompatibleBuilder<DistributedLogstreamBuilder> {

  protected DistributedLogstreamBuilder(
      String name,
      DistributedLogstreamConfig config,
      PrimitiveManagementService managementService) {
    super(DistributedLogstreamType.instance(), name, config, managementService);
  }
}
