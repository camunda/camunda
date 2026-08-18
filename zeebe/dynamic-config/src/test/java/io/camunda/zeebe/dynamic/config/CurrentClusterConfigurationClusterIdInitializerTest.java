/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import org.junit.jupiter.api.Test;

final class CurrentClusterConfigurationClusterIdInitializerTest {

  private static final MemberId LOCAL_MEMBER_ID = MemberId.from("0");

  @Test
  void shouldAssignConfiguredClusterIdWhenMissing() {
    // given
    final var configuration = CurrentClusterConfiguration.init();
    final var initializer =
        new CurrentClusterConfigurationClusterIdInitializer("my-cluster", LOCAL_MEMBER_ID);

    // when
    final var result = initializer.modify(configuration).join();

    // then
    assertThat(result.clusterId()).hasValue("my-cluster");
  }

  @Test
  void shouldGenerateClusterIdWhenNotConfigured() {
    // given
    final var configuration = CurrentClusterConfiguration.init();
    final var initializer =
        new CurrentClusterConfigurationClusterIdInitializer(null, LOCAL_MEMBER_ID);

    // when
    final var result = initializer.modify(configuration).join();

    // then
    assertThat(result.clusterId()).isPresent().get().asString().isNotBlank();
  }

  @Test
  void shouldBumpGlobalConfigurationVersionWhenAssigningClusterId() {
    // given
    final var configuration = CurrentClusterConfiguration.init();
    final var initializer =
        new CurrentClusterConfigurationClusterIdInitializer("my-cluster", LOCAL_MEMBER_ID);

    // when
    final var result = initializer.modify(configuration).join();

    // then
    assertThat(result.globalConfiguration().version())
        .isEqualTo(configuration.globalConfiguration().version() + 1);
  }

  @Test
  void shouldNotOverwriteExistingClusterId() {
    // given
    final var configuration =
        CurrentClusterConfiguration.init()
            .updateGlobalConfiguration(global -> global.setClusterId("existing-cluster"));
    final var initializer =
        new CurrentClusterConfigurationClusterIdInitializer("my-cluster", LOCAL_MEMBER_ID);

    // when
    final var result = initializer.modify(configuration).join();

    // then
    assertThat(result).isEqualTo(configuration);
    assertThat(result.clusterId()).hasValue("existing-cluster");
  }

  @Test
  void shouldOnlyRunOnCoordinator() {
    // given
    final var initializer =
        new CurrentClusterConfigurationClusterIdInitializer("my-cluster", LOCAL_MEMBER_ID);

    // then
    assertThat(initializer.filter().coordinatorOnly()).isTrue();
    assertThat(initializer.filter().localMemberId()).isEqualTo(LOCAL_MEMBER_ID);
  }
}
