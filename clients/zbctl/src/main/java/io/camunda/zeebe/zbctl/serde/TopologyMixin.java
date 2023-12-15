/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.serde;

import io.avaje.jsonb.Json;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.Topology;
import java.util.List;

@SuppressWarnings("unused")
@Json.MixIn(Topology.class)
public abstract class TopologyMixin {
  @Json.Ignore(serialize = true)
  private List<BrokerInfo> brokers;

  @Json.Ignore(serialize = true)
  private int clusterSize;

  @Json.Ignore(serialize = true)
  private int partitionsCount;

  @Json.Ignore(serialize = true)
  private int replicationFactor;

  @Json.Ignore(serialize = true)
  private String gatewayVersion;
}
