/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossipState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;

public interface ClusterConfigurationSerializer {

  byte[] encode(ClusterConfigurationGossipState gossipState);

  ClusterConfigurationGossipState decode(byte[] encodedState);

  byte[] encodeCurrentClusterConfiguration(CurrentClusterConfiguration configuration);

  CurrentClusterConfiguration decodeCurrentClusterConfiguration(
      byte[] encoded, int offset, int length);

  default CurrentClusterConfiguration decodeCurrentClusterConfiguration(final byte[] encoded) {
    return decodeCurrentClusterConfiguration(encoded, 0, encoded.length);
  }
}
