/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

final class OrphanedPartitionGroupCheckStepTest {

  private final Logger logger = mock(Logger.class);

  @Test
  void shouldWarnAboutGroupWithoutMatchingTenant() {
    // given
    final var topology =
        CurrentClusterConfiguration.init().initPartitionGroup("default").initPartitionGroup("old");

    // when
    OrphanedPartitionGroupCheckStep.warnAboutOrphanedGroups(logger, topology, Set.of("default"));

    // then
    verify(logger).warn(contains("renamed physical tenant"), eq("old"), any());
  }

  @Test
  void shouldNotWarnWhenGroupsMatchTenants() {
    // given
    final var topology =
        CurrentClusterConfiguration.init().initPartitionGroup("default").initPartitionGroup("a");

    // when
    OrphanedPartitionGroupCheckStep.warnAboutOrphanedGroups(
        logger, topology, Set.of("default", "a"));

    // then
    verify(logger, never()).warn(anyString(), any(Object.class), any(Object.class));
  }
}
