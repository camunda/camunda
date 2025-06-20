/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.unifiedconfig.UnifiedConfiguration;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import org.junit.Rule;
import org.junit.Test;

public class BrokerDifferentRuntimeDirectoryTest {
  private static final String STATE = "state";

  UnifiedConfiguration unifiedConfiguration = new UnifiedConfiguration();

  @Rule
  public final EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule(
          unifiedConfiguration,
          cfg -> {
            cfg.getCluster().setPartitionsCount(2);
            cfg.getData().setRuntimeDirectory(STATE);
          });

  @Test
  public void shouldUseConfiguredRuntimeDirectory() {
    final var runtimeDirectory = brokerRule.getBrokerBase().resolve(STATE);

    // then
    assertThat(runtimeDirectory).isNotEmptyDirectory();

    for (int i = 1; i <= 2; i++) {
      assertThat(runtimeDirectory.resolve(String.valueOf(i)))
          .describedAs(
              "Runtime directory for the partition must be created and db must be initialized.")
          .isNotEmptyDirectory();
      final var partition =
          (RaftPartition)
              brokerRule.getBroker().getBrokerContext().getPartitionManager().getRaftPartition(i);
      assertThat(partition.dataDirectory().toPath().resolve("runtime"))
          .describedAs("No runtime directory is created in the data directory")
          .doesNotExist();
    }
  }
}
