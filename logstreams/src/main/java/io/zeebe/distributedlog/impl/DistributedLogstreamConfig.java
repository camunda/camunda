/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.impl;

import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.config.PrimitiveConfig;
import io.zeebe.distributedlog.DistributedLogstreamType;

/* Define any configration parameters needed for the distributed log primitive.*/
public class DistributedLogstreamConfig extends PrimitiveConfig<DistributedLogstreamConfig> {
  @Override
  public PrimitiveType getType() {
    return DistributedLogstreamType.instance();
  }
}
