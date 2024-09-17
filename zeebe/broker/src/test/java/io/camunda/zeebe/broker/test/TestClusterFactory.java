/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.test;

import io.atomix.cluster.AtomixCluster;
import io.atomix.utils.Version;
import io.camunda.zeebe.broker.clustering.ClusterConfigFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.util.VersionUtil;

public final class TestClusterFactory {
  private TestClusterFactory() {}

  public static AtomixCluster createAtomixCluster(final BrokerCfg config) {
    final var clusterConfig = new ClusterConfigFactory().mapConfiguration(config);
    return new AtomixCluster(clusterConfig, Version.from(VersionUtil.getVersion()), "");
  }
}
