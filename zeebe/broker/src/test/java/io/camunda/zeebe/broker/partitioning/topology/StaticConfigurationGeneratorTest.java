/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.PartitioningCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StaticConfigurationGeneratorTest {

  @Test
  void shouldGenerateRoundRobinDistribution() {
    // given
    final var expectedDistribution =
        Map.of(
            1,
            Set.of(0, 1, 2),
            2,
            Set.of(1, 2, 3),
            3,
            Set.of(2, 3, 4),
            4,
            Set.of(3, 4, 5),
            5,
            Set.of(4, 5, 0),
            6,
            Set.of(5, 0, 1));

    final PartitioningCfg partitioningCfg = new PartitioningCfg();
    partitioningCfg.setScheme(Scheme.ROUND_ROBIN);
    final ClusterCfg clusterCfg = new ClusterCfg();
    clusterCfg.setClusterSize(6);
    clusterCfg.setPartitionsCount(6);
    clusterCfg.setReplicationFactor(3);
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.setCluster(clusterCfg);
    brokerCfg.getExperimental().setPartitioning(partitioningCfg);
    brokerCfg.setExporters(Map.of());

    // when
    final var partitionDistribution =
        StaticConfigurationGenerator.getStaticConfiguration(brokerCfg, MemberId.from("1"))
            .generatePartitionDistribution();

    // then
    // RoundRobinPartitionDistributorTest verifies more cases.
    final var actualDistribution = getDistribution(partitionDistribution);
    assertThat(actualDistribution).containsExactlyInAnyOrderEntriesOf(expectedDistribution);
  }

  private Map<Integer, Set<Integer>> getDistribution(
      final Set<PartitionMetadata> partitionDistribution) {
    return partitionDistribution.stream()
        .collect(
            Collectors.toMap(
                p -> p.id().id(),
                p ->
                    p.members().stream()
                        .map(m -> Integer.valueOf(m.id()))
                        .collect(Collectors.toSet())));
  }

  @Test
  void shouldGenerateFixedDistribution() throws IOException {
    final var expectedDistribution =
        Map.of(1, Set.of(0, 1, 2), 2, Set.of(1, 2, 0), 3, Set.of(0, 1, 2));
    final String config =
        """
fixed:
   - partitionId: 1
     nodes:
       - nodeId: 0
         priority: 1
       - nodeId: 1
         priority: 2
       - nodeId: 2
         priority: 3
   - partitionId: 2
     nodes:
       - nodeId: 0
         priority: 3
       - nodeId: 1
         priority: 2
       - nodeId: 2
         priority: 1
   - partitionId: 3
     nodes:
       - nodeId: 0
         priority: 2
       - nodeId: 1
         priority: 3
       - nodeId: 2
         priority: 2
""";

    final var partitioningCfg =
        new ObjectMapper(new YAMLFactory()).readValue(config, PartitioningCfg.class);
    final var clusterCfg = new ClusterCfg();
    clusterCfg.setClusterSize(3);
    clusterCfg.setPartitionsCount(3);
    clusterCfg.setReplicationFactor(3);
    final BrokerCfg brokerCfg = new BrokerCfg();
    brokerCfg.setCluster(clusterCfg);
    brokerCfg.getExperimental().setPartitioning(partitioningCfg);

    // when
    final var partitionDistribution =
        StaticConfigurationGenerator.getStaticConfiguration(brokerCfg, MemberId.from("1"))
            .generatePartitionDistribution();

    // then
    // FixedPartitionDistributorTest verifies more cases.
    final var actualDistribution = getDistribution(partitionDistribution);
    assertThat(actualDistribution).containsExactlyInAnyOrderEntriesOf(expectedDistribution);
  }

  private static MemberId member(final int id) {
    return MemberId.from(String.valueOf(id));
  }
}
