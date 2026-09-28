/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossipState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;

public interface ClusterConfigurationSerializer {

  byte[] encode(ClusterConfigurationGossipState gossipState);

  ClusterConfigurationGossipState decode(byte[] encodedState);

  /**
   * Encodes the legacy {@code ClusterTopology} proto. No longer gossiped; used only by tests that
   * fabricate a pre-8.10 persisted configuration file for {@code
   * PersistedCurrentClusterConfiguration}'s {@code VERSION_LEGACY} migration branch.
   */
  byte[] encode(ClusterConfiguration clusterConfiguration);

  /**
   * Decodes the legacy {@code ClusterTopology} proto. Used only by {@code
   * PersistedCurrentClusterConfiguration}'s {@code VERSION_LEGACY} migration branch, to read a
   * configuration file persisted before 8.10.
   */
  ClusterConfiguration decodeClusterTopology(
      byte[] encodedClusterTopology, final int offset, final int length);

  byte[] encodeCurrentClusterConfiguration(CurrentClusterConfiguration configuration);

  CurrentClusterConfiguration decodeCurrentClusterConfiguration(
      byte[] encoded, int offset, int length);
}
