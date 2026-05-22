/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.qa.util.cluster.TestCluster;

final class NonZoneAwareClusterEndpointIT extends ClusterEndpointIT {

  @Override
  @SuppressWarnings("resource")
  protected TestCluster createCluster(final int replicationFactor) {
    return TestCluster.builder()
        .withEmbeddedGateway(true)
        .withBrokersCount(BROKER_COUNT)
        .withPartitionsCount(PARTITION_COUNT)
        .withReplicationFactor(replicationFactor)
        .build()
        .start();
  }

  @Override
  protected String zone() {
    return null;
  }

  @Override
  protected BrokerId brokerId(final int nodeIdx) {
    return new BrokerId.Integer(nodeIdx);
  }
}
