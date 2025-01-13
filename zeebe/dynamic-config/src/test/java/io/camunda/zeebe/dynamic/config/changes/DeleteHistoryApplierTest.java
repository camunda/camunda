/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class DeleteHistoryApplierTest {

  @Test
  void shouldSuccessDeleteHistoryOnInitIfNoPartitionExists() {
    // given
    final MemberId memberId = MemberId.from("1");
    final ClusterChangeExecutor clusterChangeExecutor =
        new ClusterChangeExecutor.NoopClusterChangeExecutor();
    final var deleteHistoryApplier = new DeleteHistoryApplier(memberId, clusterChangeExecutor);

    final ClusterConfiguration clusterConfigurationWithMember =
        ClusterConfiguration.init().addMember(memberId, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = deleteHistoryApplier.init(clusterConfigurationWithMember);

    // then
    EitherAssert.assertThat(result).isRight();
  }

  @Test
  void shouldFailDeleteHistoryOnInitIfPartitionsExist() {
    // given
    final MemberId memberId = MemberId.from("1");
    final int partitionId = 2;
    final ClusterChangeExecutor clusterChangeExecutor =
        new ClusterChangeExecutor.NoopClusterChangeExecutor();
    final var deleteHistoryApplier = new DeleteHistoryApplier(memberId, clusterChangeExecutor);

    final ClusterConfiguration clusterConfigWithPartition =
        ClusterConfiguration.init()
            .addMember(memberId, MemberState.initializeAsActive(Map.of()))
            .updateMember(
                memberId,
                m ->
                    m.addPartition(
                        partitionId,
                        PartitionState.active(
                            1, new DynamicPartitionConfig(new ExportersConfig(Map.of())))));
    // when
    final var result = deleteHistoryApplier.init(clusterConfigWithPartition);

    // then
    EitherAssert.assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("partitions still exist");
  }

  @Test
  void shouldPreserveSameConfigurationAfterApply() {
    // given
    final MemberId member1Id = MemberId.from("1");
    final MemberId member2Id = MemberId.from("2");
    final ClusterChangeExecutor clusterChangeExecutor =
        new ClusterChangeExecutor.NoopClusterChangeExecutor();

    final ClusterConfiguration initialConfiguration =
        ClusterConfiguration.init()
            .addMember(member1Id, MemberState.initializeAsActive(Map.of()))
            .addMember(member2Id, MemberState.initializeAsActive(Map.of()));
    final var deleteHistoryApplier = new DeleteHistoryApplier(member1Id, clusterChangeExecutor);
    final var initializedConfiguration =
        deleteHistoryApplier.init(initialConfiguration).get().apply(initialConfiguration);
    final var updater = deleteHistoryApplier.apply().join();
    final ClusterConfiguration updatedConfiguration = updater.apply(initializedConfiguration);

    // when
    ClusterConfigurationAssert.assertThatClusterTopology(updatedConfiguration)
        .isEqualTo(initialConfiguration);
  }
}
