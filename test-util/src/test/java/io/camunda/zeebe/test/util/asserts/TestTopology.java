/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.asserts;

import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.PartitionBrokerHealth;
import io.camunda.zeebe.client.api.response.PartitionBrokerRole;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.util.VersionUtil;
import java.util.List;

/**
 * Topology implementation used in tests to avoid coupling tests to the implementation of the client
 * and its protocol.
 */
record TestTopology(
    int clusterSize, int partitionsCount, int replicationFactor, List<BrokerInfo> brokers)
    implements Topology {

  private static final String HARDCODED_VERSION = VersionUtil.getVersion();

  @Override
  public List<BrokerInfo> getBrokers() {
    return brokers;
  }

  @Override
  public int getClusterSize() {
    return clusterSize;
  }

  @Override
  public int getPartitionsCount() {
    return partitionsCount;
  }

  @Override
  public int getReplicationFactor() {
    return replicationFactor;
  }

  @Override
  public String getGatewayVersion() {
    return HARDCODED_VERSION;
  }

  record TestPartition(int partitionId, PartitionBrokerRole role, PartitionBrokerHealth health)
      implements PartitionInfo {

    @Override
    public int getPartitionId() {
      return partitionId;
    }

    @Override
    public PartitionBrokerRole getRole() {
      return role;
    }

    @Override
    public boolean isLeader() {
      return role == PartitionBrokerRole.LEADER;
    }

    @Override
    public PartitionBrokerHealth getHealth() {
      return health;
    }
  }

  record TestBroker(int nodeId, List<PartitionInfo> partitions) implements BrokerInfo {

    @Override
    public int getNodeId() {
      return nodeId;
    }

    @Override
    public String getHost() {
      return "foo";
    }

    @Override
    public int getPort() {
      return 26502;
    }

    @Override
    public String getAddress() {
      return getHost() + ":" + getPort();
    }

    @Override
    public String getVersion() {
      return HARDCODED_VERSION;
    }

    @Override
    public List<PartitionInfo> getPartitions() {
      return partitions;
    }
  }
}
