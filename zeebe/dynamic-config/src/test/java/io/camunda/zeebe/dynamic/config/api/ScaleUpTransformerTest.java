/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

public class ScaleUpTransformerTest {

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  private final int clusterSize = 5;
  private final int replicationFactor = 3;

  @Property
  void shouldFailIfDesiredPartitionCountIsLessThanNewPartitions(
      @ForAll @IntRange(min = 1, max = 100) final int currentPartitionCount,
      @ForAll @IntRange(min = 1, max = 100) final int desiredPartitionCount,
      @ForAll @IntRange(min = 1, max = 100) final int newPartitionCount) {
    scaleUpWithValidation(currentPartitionCount, desiredPartitionCount, newPartitionCount, null);
  }

  @Property
  void shouldGenerateOperationForAllPartition1Members(
      @ForAll @IntRange(min = 1, max = 100) final int currentPartitionCount,
      @ForAll @IntRange(min = 1, max = 100) final int newPartitionCount) {
    final var desiredPartitionCount = currentPartitionCount + newPartitionCount;
    scaleUpWithValidation(
        currentPartitionCount,
        desiredPartitionCount,
        newPartitionCount,
        operations -> {
          assertThat(operations).hasSize(3); // 3 operation for each replica
          final var lowestMemberId = MemberId.from("1");
          final var newPartitions =
              partitionsInRange(currentPartitionCount + 1, desiredPartitionCount + 1);
          assertThat(operations)
              .allSatisfy(
                  operation -> {
                    assertThat(operation.memberId()).isEqualTo(lowestMemberId);
                  });
          assertThat(operations)
              .isEqualTo(
                  List.of(
                      new StartPartitionScaleUp(lowestMemberId, desiredPartitionCount),
                      new AwaitRedistributionCompletion(
                          lowestMemberId, desiredPartitionCount, newPartitions),
                      new AwaitRelocationCompletion(
                          lowestMemberId, desiredPartitionCount, newPartitions)));
        });
  }

  public void scaleUpWithValidation(
      final int currentPartitionCount,
      final int desiredPartitionCount,
      final int newPartitionCount,
      final Consumer<List<ClusterConfigurationChangeOperation>> whenRight) {
    final var distribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(
                IntStream.range(1, clusterSize)
                    .mapToObj(id -> MemberId.from(Integer.toString(id)))
                    .collect(Collectors.toSet()),
                partitionsInRange(1, 1 + currentPartitionCount).stream()
                    .map(i -> PartitionId.from("temp", i))
                    .toList(),
                replicationFactor);
    final var config = ConfigurationUtil.getClusterConfigFrom(true, distribution, partitionConfig);
    final var transformer =
        new ScaleUpRequestTransformer(
            desiredPartitionCount,
            partitionsInRange(
                currentPartitionCount + 1, newPartitionCount + currentPartitionCount + 1));
    final var operations = transformer.operations(config);
    if (desiredPartitionCount < currentPartitionCount) {
      EitherAssert.assertThat(operations).isLeft();
    } else if (desiredPartitionCount != currentPartitionCount + newPartitionCount) {
      EitherAssert.assertThat(operations).isLeft();
    } else {
      EitherAssert.assertThat(operations).right();
      if (whenRight != null) {
        whenRight.accept(operations.get());
      }
    }
  }

  SortedSet<Integer> partitionsInRange(final int from, final int to) {
    return new TreeSet<>(IntStream.range(from, to).boxed().toList());
  }
}
