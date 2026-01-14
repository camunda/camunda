/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.dynamic.nodeid.Lease.VersionMappings;
import io.camunda.zeebe.dynamic.nodeid.repository.Metadata;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease.Initialized;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease.Uninitialized;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RepositoryNodeIdProviderReadinessCheckerTest {
  private NodeIdRepository nodeIdRepository;
  private RepositoryNodeIdProviderReadinessChecker readinessChecker;
  private NodeInstance currentNodeInstance;
  private final NodeInstance nodeInstance2 = new NodeInstance(2, Version.of(2L));
  private final int clusterSize = 3;

  @BeforeEach
  public void setUp() {
    nodeIdRepository = mock(NodeIdRepository.class);
    currentNodeInstance = new NodeInstance(0, Version.of(100L));
    readinessChecker =
        new RepositoryNodeIdProviderReadinessChecker(
            clusterSize, currentNodeInstance, nodeIdRepository, Duration.ofMillis(10));
  }

  @Test
  public void shouldCompleteWhenAllNodesAreUpToDate() {
    // given
    final int clusterSize = 3;

    final var versionMappings =
        new VersionMappings(Map.of(0, Version.of(100L), 1, Version.of(101L), 2, Version.of(102L)));

    // Mock all nodes as up to date
    for (int i = 0; i < clusterSize; i++) {
      final var lease =
          new Lease(
              "task" + i,
              System.currentTimeMillis(),
              new NodeInstance(i, versionMappings.mappingsByNodeId().get(i)),
              versionMappings);
      when(nodeIdRepository.getLease(i)).thenReturn(getInitializedLease(lease));
    }

    // when
    final var result = readinessChecker.waitUntilAllNodesAreUpToDate();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    // should verify other leases from other two nodes
    verify(nodeIdRepository, times(clusterSize - 1)).getLease(anyInt());
  }

  @Test
  public void shouldRetryWhenSomeNodesAreNotUpToDate() {
    // given
    final var outdatedVersionMappings =
        new VersionMappings(Map.of(0, Version.of(99), 1, Version.of(101L)));
    final var currentVersionMappings =
        new VersionMappings(Map.of(0, Version.of(100L), 1, Version.of(101L)));

    final var lease2 =
        new Lease("task2", System.currentTimeMillis(), nodeInstance2, currentVersionMappings);
    final var lease1Outdated =
        new Lease(
            "task1",
            System.currentTimeMillis(),
            new NodeInstance(1, Version.of(101L)),
            outdatedVersionMappings);
    final var lease1Updated =
        new Lease(
            "task1",
            System.currentTimeMillis(),
            new NodeInstance(1, Version.of(101L)),
            currentVersionMappings);

    // First check: node 1 is outdated
    when(nodeIdRepository.getLease(2)).thenReturn(getInitializedLease(lease2));
    when(nodeIdRepository.getLease(1))
        .thenReturn(getInitializedLease(lease1Outdated))
        .thenReturn(getInitializedLease(lease1Updated));

    // when
    final var result = readinessChecker.waitUntilAllNodesAreUpToDate();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    verify(nodeIdRepository, times(1)).getLease(2);
    verify(nodeIdRepository, times(2)).getLease(1);
  }

  @Test
  public void shouldRetryWhenNodeLeaseIsUninitialized() {
    // given
    final var versionMappings =
        new VersionMappings(Map.of(0, Version.of(100L), 1, Version.of(101L)));
    final var lease2 =
        new Lease("task2", System.currentTimeMillis(), nodeInstance2, versionMappings);
    final var lease1 =
        new Lease(
            "task1",
            System.currentTimeMillis(),
            new NodeInstance(1, Version.of(101L)),
            versionMappings);

    when(nodeIdRepository.getLease(2)).thenReturn(getInitializedLease(lease2));
    when(nodeIdRepository.getLease(1))
        .thenReturn(new Uninitialized(new NodeInstance(1, Version.of(0L)), "test"))
        .thenReturn(getInitializedLease(lease1));

    // when
    final var result = readinessChecker.waitUntilAllNodesAreUpToDate();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    verify(nodeIdRepository, times(1)).getLease(2);
    verify(nodeIdRepository, times(2)).getLease(1);
  }

  @Test
  public void shouldContinueCheckingWhenRepositoryThrowsException() {
    // given
    final var versionMappings =
        new VersionMappings(Map.of(0, Version.of(100L), 1, Version.of(101L)));
    final var lease2 =
        new Lease("task2", System.currentTimeMillis(), nodeInstance2, versionMappings);
    final var lease1 =
        new Lease(
            "task1",
            System.currentTimeMillis(),
            new NodeInstance(1, Version.of(101L)),
            versionMappings);

    when(nodeIdRepository.getLease(2)).thenReturn(getInitializedLease(lease2));
    when(nodeIdRepository.getLease(1))
        .thenThrow(new RuntimeException("Repository error"))
        .thenReturn(getInitializedLease(lease1));

    // when
    final var result = readinessChecker.waitUntilAllNodesAreUpToDate();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    verify(nodeIdRepository, times(1)).getLease(2);
    verify(nodeIdRepository, times(2)).getLease(1);
  }

  @Test
  public void shouldNotConsiderNodeUpToDateWhenVersionMissing() {
    // given
    final var versionMappingsWithoutNode0 = new VersionMappings(Map.of(1, Version.of(101L)));
    final var versionMappingsComplete =
        new VersionMappings(Map.of(0, Version.of(100L), 1, Version.of(101L)));

    final var lease2 =
        new Lease("task2", System.currentTimeMillis(), nodeInstance2, versionMappingsComplete);
    final var lease1Incomplete =
        new Lease(
            "task1",
            System.currentTimeMillis(),
            new NodeInstance(1, Version.of(101L)),
            versionMappingsWithoutNode0);
    final var lease1Complete =
        new Lease(
            "task1",
            System.currentTimeMillis(),
            new NodeInstance(1, Version.of(101L)),
            versionMappingsComplete);

    when(nodeIdRepository.getLease(2)).thenReturn(getInitializedLease(lease2));
    when(nodeIdRepository.getLease(1))
        .thenReturn(getInitializedLease(lease1Incomplete))
        .thenReturn(getInitializedLease(lease1Complete));

    // when
    final var result = readinessChecker.waitUntilAllNodesAreUpToDate();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    verify(nodeIdRepository, times(1)).getLease(2);
    verify(nodeIdRepository, times(2)).getLease(1);
  }

  @Test
  public void shouldNotConsiderNodeUpToDateWhenVersionMismatch() {
    // given
    final var versionMappingsWrongVersion =
        new VersionMappings(Map.of(0, Version.of(99L), 1, Version.of(101L)));
    final var versionMappingsCorrect =
        new VersionMappings(Map.of(0, Version.of(100L), 1, Version.of(101L)));

    final var lease2 =
        new Lease("task2", System.currentTimeMillis(), nodeInstance2, versionMappingsCorrect);
    final var lease1Wrong =
        new Lease(
            "task1",
            System.currentTimeMillis(),
            new NodeInstance(1, Version.of(101L)),
            versionMappingsWrongVersion);
    final var lease1Correct =
        new Lease(
            "task1",
            System.currentTimeMillis(),
            new NodeInstance(1, Version.of(101L)),
            versionMappingsCorrect);

    when(nodeIdRepository.getLease(2)).thenReturn(getInitializedLease(lease2));
    when(nodeIdRepository.getLease(1))
        .thenReturn(getInitializedLease(lease1Wrong))
        .thenReturn(getInitializedLease(lease1Correct));

    // when
    final var result = readinessChecker.waitUntilAllNodesAreUpToDate();

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(5));
    verify(nodeIdRepository, times(1)).getLease(2);
    verify(nodeIdRepository, times(2)).getLease(1);
  }

  private static Initialized getInitializedLease(final Lease lease0) {
    return new Initialized(Metadata.fromLease(lease0), lease0, "test");
  }
}
