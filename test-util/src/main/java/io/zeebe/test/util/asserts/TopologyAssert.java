/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.asserts;

import io.zeebe.client.api.response.BrokerInfo;
import io.zeebe.client.api.response.Topology;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;

public class TopologyAssert extends AbstractAssert<TopologyAssert, Topology> {

  public TopologyAssert(final Topology topology) {
    super(topology, TopologyAssert.class);
  }

  public static TopologyAssert assertThat(Topology actual) {
    return new TopologyAssert(actual);
  }

  public final TopologyAssert isComplete(final int clusterSize, final int partitionCount) {
    isNotNull();

    final List<BrokerInfo> brokers = actual.getBrokers();

    if (brokers.size() != clusterSize) {
      failWithMessage("Expected broker count to be <%s> but was <%s>", clusterSize, brokers.size());
    }

    final List<BrokerInfo> brokersWithUnexpectedPartitionCount =
        brokers.stream()
            .filter(b -> b.getPartitions().size() != partitionCount)
            .collect(Collectors.toList());

    if (!brokersWithUnexpectedPartitionCount.isEmpty()) {
      failWithMessage(
          "Expected <%s> partitions at each broker, but found brokers with different partition count <%s>",
          partitionCount, brokersWithUnexpectedPartitionCount);
    }

    return this;
  }
}
