/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.topology;

import io.camunda.zeebe.management.cluster.BrokerStateCode;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.management.cluster.PostOperationResponse;
import io.camunda.zeebe.management.cluster.TopologyChange.StatusEnum;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import java.time.OffsetDateTime;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.Assertions;

public final class ClusterActuatorAssert
    extends AbstractObjectAssert<ClusterActuatorAssert, ClusterActuator> {

  public ClusterActuatorAssert(final ClusterActuator clusterActuator, final Class<?> selfType) {
    super(clusterActuator, selfType);
  }

  public static ClusterActuatorAssert assertThat(final ClusterActuator actuator) {
    return new ClusterActuatorAssert(actuator, ClusterActuatorAssert.class);
  }

  public static ClusterActuatorAssert assertThat(final TestCluster actuator) {
    return new ClusterActuatorAssert(
        ClusterActuator.of(actuator.anyGateway()), ClusterActuatorAssert.class);
  }

  public ClusterActuatorAssert doesNotHaveBroker(final int brokerId) {
    Assertions.assertThat(actual.getTopology().getBrokers())
        .filteredOn(b -> b.getId() == brokerId)
        .isEmpty();
    return this;
  }

  public ClusterActuatorAssert hasAppliedChanges(final PostOperationResponse response) {
    final var expectedTopology = response.getExpectedTopology();
    final var currentTopology = actual.getTopology().getBrokers();
    Assertions.assertThat(currentTopology)
        .usingRecursiveComparison()
        .ignoringFieldsOfTypes(OffsetDateTime.class)
        .isEqualTo(expectedTopology);
    return this;
  }

  public ClusterActuatorAssert hasCompletedChanges(final PostOperationResponse response) {
    final var currentChange = actual.getTopology().getChange();
    Assertions.assertThat(currentChange).isNotNull();
    Assertions.assertThat(currentChange.getId()).isEqualTo(response.getChangeId());
    Assertions.assertThat(currentChange.getStatus()).isEqualTo(StatusEnum.COMPLETED);
    return this;
  }

  public ClusterActuatorAssert brokerHasPartition(final int brokerId, final int partitionId) {
    Assertions.assertThat(actual.getTopology().getBrokers())
        .filteredOn(b -> b.getId() == brokerId)
        .singleElement()
        .matches(
            b -> b.getPartitions().stream().anyMatch(p -> p.getId() == partitionId),
            "Broker %d has partition %d".formatted(brokerId, partitionId));
    return this;
  }

  public ClusterActuatorAssert brokerDoesNotHavePartition(
      final int brokerId, final int partitionId) {
    Assertions.assertThat(actual.getTopology().getBrokers())
        .filteredOn(b -> b.getId() == brokerId)
        .singleElement()
        .matches(
            b -> b.getPartitions().stream().noneMatch(p -> p.getId() == partitionId),
            "Broker %d does not have partition %d".formatted(brokerId, partitionId));
    return this;
  }

  public ClusterActuatorAssert hasActiveBroker(final int brokerId) {
    Assertions.assertThat(actual.getTopology().getBrokers())
        .filteredOn(b -> b.getId() == brokerId)
        .singleElement()
        .matches(
            b -> b.getState().equals(BrokerStateCode.ACTIVE),
            "Cluster does not have broker %d in Active state".formatted(brokerId));
    return this;
  }

  public ClusterActuatorAssert doesNotHavePendingChanges() {
    final var currentChange = actual.getTopology().getChange();
    Assertions.assertThat(currentChange).isNotNull();
    Assertions.assertThat(currentChange.getStatus()).isEqualTo(StatusEnum.COMPLETED);
    return this;
  }

  public ClusterActuatorAssert brokerHasPartitionAtState(
      final int brokerId, final int partitionId, final PartitionStateCode state) {
    Assertions.assertThat(actual.getTopology().getBrokers())
        .filteredOn(b -> b.getId() == brokerId)
        .singleElement()
        .matches(
            b ->
                b.getPartitions().stream()
                    .anyMatch(p -> p.getId() == partitionId && p.getState().equals(state)),
            "Broker %d has partition %d".formatted(brokerId, partitionId));
    return this;
  }
}
