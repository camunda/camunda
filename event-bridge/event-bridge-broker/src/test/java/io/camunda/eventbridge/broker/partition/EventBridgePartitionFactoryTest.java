/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link EventBridgePartitionFactory}. */
final class EventBridgePartitionFactoryTest {

  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Nested
  class PartitionDirectoryTest {

    @Test
    void shouldComputePartitionDirectoryUnderDataRoot(@TempDir final Path tmp) {
      // given
      final var properties = propertiesWithDataDir(tmp.toString());
      final var factory = new EventBridgePartitionFactory(properties);

      // when
      final Path dir = factory.getPartitionDirectory(3);

      // then — structure: {data}/{GROUP_NAME}/partitions/3
      assertThat(dir)
          .isEqualTo(
              tmp.resolve(EventBridgePartitionFactory.GROUP_NAME)
                  .resolve("partitions")
                  .resolve("3"));
    }

    @Test
    void shouldCreatePartitionDirectoryWhenMissing(@TempDir final Path tmp) {
      // given
      final var properties = propertiesWithDataDir(tmp.toString());
      final var factory = new EventBridgePartitionFactory(properties);
      final var memberId = MemberId.from("broker-0");

      // when
      assertThatNoException()
          .isThrownBy(() -> factory.createPartition(0, Set.of(memberId), memberId, meterRegistry));

      // then
      assertThat(factory.getPartitionDirectory(0)).isDirectory();
    }

    @Test
    void shouldThrowWhenDataRootCannotBeCreated(@TempDir final Path tmp) throws IOException {
      // given – use a file as the data root so directory creation must fail
      final var file = tmp.resolve("file.txt");
      Files.createFile(file);
      // Nest a path inside the file (impossible to create as a directory)
      final var properties = propertiesWithDataDir(file.toString());
      final var factory = new EventBridgePartitionFactory(properties);
      final var memberId = MemberId.from("broker-0");

      // when / then
      assertThatThrownBy(
              () -> factory.createPartition(0, Set.of(memberId), memberId, meterRegistry))
          .isInstanceOf(java.io.UncheckedIOException.class);
    }
  }

  @Nested
  class PartitionConfigurationTest {

    @Test
    void shouldUseEventBridgeGroupName(@TempDir final Path tmp) {
      // given
      final var factory = factoryWithTmp(tmp);
      final var memberId = MemberId.from("broker-0");

      // when
      final var partition = factory.createPartition(1, Set.of(memberId), memberId, meterRegistry);

      // then
      assertThat(partition.id().group()).isEqualTo(EventBridgePartitionFactory.GROUP_NAME);
    }

    @Test
    void shouldSetPartitionId(@TempDir final Path tmp) {
      // given
      final var factory = factoryWithTmp(tmp);
      final var memberId = MemberId.from("broker-0");

      // when
      final var partition = factory.createPartition(7, Set.of(memberId), memberId, meterRegistry);

      // then
      assertThat(partition.id().id()).isEqualTo(7);
    }

    @Test
    void shouldIncludeAllMembersInPartitionMetadata(@TempDir final Path tmp) {
      // given
      final var factory = factoryWithTmp(tmp);
      final var m0 = MemberId.from("broker-0");
      final var m1 = MemberId.from("broker-1");
      final var m2 = MemberId.from("broker-2");
      final var members = Set.of(m0, m1, m2);

      // when
      final var partition = factory.createPartition(0, members, m0, meterRegistry);

      // then
      assertThat(partition.members()).containsExactlyInAnyOrderElementsOf(members);
    }

    @Test
    void shouldDisablePriorityElection(@TempDir final Path tmp) {
      // given
      final var factory = factoryWithTmp(tmp);
      final var memberId = MemberId.from("broker-0");

      // when
      final var partition = factory.createPartition(0, Set.of(memberId), memberId, meterRegistry);

      // then
      assertThat(partition.getPartitionConfig().isPriorityElectionEnabled()).isFalse();
    }

    @Test
    void shouldUseEventBridgeEngineName(@TempDir final Path tmp) {
      // given
      final var factory = factoryWithTmp(tmp);
      final var memberId = MemberId.from("broker-0");

      // when
      final var partition = factory.createPartition(0, Set.of(memberId), memberId, meterRegistry);

      // then
      assertThat(partition.getPartitionConfig().getEngineName()).isEqualTo("event-bridge");
    }

    @Test
    void shouldNotUseLegacySubjects(@TempDir final Path tmp) {
      // given
      final var factory = factoryWithTmp(tmp);
      final var memberId = MemberId.from("broker-0");

      // when
      final var partition = factory.createPartition(0, Set.of(memberId), memberId, meterRegistry);

      // then
      assertThat(partition.getPartitionConfig().isSendOnLegacySubject()).isFalse();
      assertThat(partition.getPartitionConfig().isReceiveOnLegacySubject()).isFalse();
    }

    @Test
    void shouldAcceptExplicitPartitionDirectory(@TempDir final Path tmp) throws IOException {
      // given
      final var factory = factoryWithTmp(tmp);
      final var memberId = MemberId.from("broker-0");
      final Path explicitDir = tmp.resolve("explicit");
      Files.createDirectories(explicitDir);

      // when / then – no exception, partition created at the given path
      assertThatNoException()
          .isThrownBy(
              () ->
                  factory.createPartition(
                      5, Set.of(memberId), memberId, explicitDir, meterRegistry));
      assertThat(explicitDir).isDirectory();
    }
  }

  // -------------------------------------------------------------------------
  // Helpers

  private static EventBridgePartitionFactory factoryWithTmp(final Path tmp) {
    return new EventBridgePartitionFactory(propertiesWithDataDir(tmp.toString()));
  }

  private static EventBridgeProperties propertiesWithDataDir(final String dataDir) {
    return new EventBridgeProperties(
        new EventBridgeProperties.DataProperties(dataDir), null, null, null, null, null, null, null);
  }
}
