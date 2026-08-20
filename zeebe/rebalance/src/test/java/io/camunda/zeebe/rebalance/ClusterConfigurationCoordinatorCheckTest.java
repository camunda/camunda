/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import org.junit.jupiter.api.Test;

final class ClusterConfigurationCoordinatorCheckTest {

  private static final MemberId COORDINATOR = MemberId.from("0");
  private static final MemberId OTHER_MEMBER = MemberId.from("1");

  @Test
  void shouldAcceptTheLowestIdMemberOfTheClusterConfiguration() {
    // given
    final var configuration = configurationOf(COORDINATOR, OTHER_MEMBER);
    final var check = new ClusterConfigurationCoordinatorCheck(() -> configuration);

    // when
    final var rejection =
        check.validate(COORDINATOR, configuration.globalConfiguration().version());

    // then
    assertThat(rejection)
        .as("the coordinator holds no partitions at all, which is not a reason to refuse it")
        .isEmpty();
  }

  @Test
  void shouldRefuseAMemberThatIsNotTheLowestId() {
    // given
    final var configuration = configurationOf(COORDINATOR, OTHER_MEMBER);
    final var check = new ClusterConfigurationCoordinatorCheck(() -> configuration);

    // when
    final var rejection =
        check.validate(OTHER_MEMBER, configuration.globalConfiguration().version());

    // then
    assertThat(rejection).contains(LeadershipTransferResult.NOT_COORDINATOR);
  }

  @Test
  void shouldRefuseACoordinatorWorkingFromAnOlderConfiguration() {
    // given
    final var configuration = configurationOf(COORDINATOR, OTHER_MEMBER);
    final var check = new ClusterConfigurationCoordinatorCheck(() -> configuration);

    // when
    final var rejection =
        check.validate(COORDINATOR, configuration.globalConfiguration().version() - 1);

    // then
    assertThat(rejection).contains(LeadershipTransferResult.STALE_CONFIGURATION);
  }

  @Test
  void shouldRefuseACoordinatorWorkingFromANewerConfiguration() {
    // given
    final var configuration = configurationOf(COORDINATOR, OTHER_MEMBER);
    final var check = new ClusterConfigurationCoordinatorCheck(() -> configuration);

    // when
    final var rejection =
        check.validate(COORDINATOR, configuration.globalConfiguration().version() + 1);

    // then
    assertThat(rejection)
        .as(
            "this node hasn't seen that configuration yet, so it cannot validate the claimed"
                + " coordinator against it")
        .contains(LeadershipTransferResult.STALE_CONFIGURATION);
  }

  @Test
  void shouldRefuseEveryRequesterWithoutAConfigurationToCheckAgainst() {
    // given
    final var check =
        new ClusterConfigurationCoordinatorCheck(CurrentClusterConfiguration::uninitialized);

    // when
    final var rejection = check.validate(COORDINATOR, 1);

    // then
    assertThat(rejection).contains(LeadershipTransferResult.STALE_CONFIGURATION);
  }

  private static CurrentClusterConfiguration configurationOf(final MemberId... members) {
    var configuration = CurrentClusterConfiguration.init();
    for (final var member : members) {
      configuration =
          configuration.updateGlobalConfiguration(
              global -> global.addMember(member, BrokerState.initializeAsActive()));
    }
    return configuration;
  }
}
