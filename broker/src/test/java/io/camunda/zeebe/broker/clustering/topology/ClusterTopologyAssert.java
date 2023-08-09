/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.clustering.topology;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import java.util.Collection;
import org.assertj.core.api.AbstractAssert;

final class ClusterTopologyAssert extends AbstractAssert<ClusterTopologyAssert, ClusterTopology> {

  private ClusterTopologyAssert(final ClusterTopology clusterTopology, final Class<?> selfType) {
    super(clusterTopology, selfType);
  }

  static ClusterTopologyAssert assertThatClusterTopology(final ClusterTopology actual) {
    return new ClusterTopologyAssert(actual, ClusterTopologyAssert.class);
  }

  ClusterTopologyAssert hasMemberWithPartitions(
      final int member, final Collection<Integer> partitionIds) {
    final var memberId = MemberId.from(Integer.toString(member));
    assertThat(actual.members()).containsKey(memberId);
    assertThat(actual.members().get(memberId).partitions()).containsOnlyKeys(partitionIds);
    return this;
  }
}
