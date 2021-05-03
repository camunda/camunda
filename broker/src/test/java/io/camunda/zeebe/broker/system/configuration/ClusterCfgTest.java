/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class ClusterCfgTest {

  @Test
  public void shouldSanitizeContactPoints() {
    // given
    final ClusterCfg sutClusterConfig = new ClusterCfg();
    final List<String> input = Arrays.asList("", "foo ", null, "   ", "bar");
    final List<String> expected = Arrays.asList("foo", "bar");

    // when
    sutClusterConfig.setInitialContactPoints(input);

    // then
    final List<String> actual = sutClusterConfig.getInitialContactPoints();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void shouldGeneratePartitionIds() {
    // given
    final ClusterCfg clusterCfg = new ClusterCfg();
    clusterCfg.setPartitionsCount(8);

    // when
    clusterCfg.init(new BrokerCfg(), "");

    // then
    assertThat(clusterCfg.getPartitionIds()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
  }
}
