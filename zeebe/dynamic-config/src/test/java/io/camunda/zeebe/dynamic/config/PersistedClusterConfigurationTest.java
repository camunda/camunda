/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration.ChecksumMismatch;
import io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration.MissingHeader;
import io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration.UnexpectedVersion;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PersistedClusterConfigurationTest {
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldDetectFileWithMoreDataThanExpected() throws IOException {
    // given
    final var tmp = Files.createTempDirectory("topology");
    final var topologyFile = tmp.resolve("topology.meta");
    final var serializer = new ProtoBufSerializer();
    final var persistedClusterTopology =
        PersistedClusterConfiguration.ofFile(topologyFile, serializer);
    persistedClusterTopology.update(ClusterConfiguration.init());

    // when
    Files.write(topologyFile, "more data than expected".getBytes(), StandardOpenOption.APPEND);

    // then
    Assertions.assertThatCode(() -> PersistedClusterConfiguration.ofFile(topologyFile, serializer))
        .isInstanceOf(ChecksumMismatch.class);
  }

  @Test
  void shouldDetectFileWithBrokenHeader() throws IOException {
    // given
    final var tmp = Files.createTempDirectory("topology");
    final var topologyFile = tmp.resolve("topology.meta");
    final var serializer = new ProtoBufSerializer();
    final var persistedClusterTopology =
        PersistedClusterConfiguration.ofFile(topologyFile, serializer);
    persistedClusterTopology.update(ClusterConfiguration.init());

    // when
    Files.write(topologyFile, "broken header".getBytes(), StandardOpenOption.WRITE);

    // then
    Assertions.assertThatCode(() -> PersistedClusterConfiguration.ofFile(topologyFile, serializer))
        .isInstanceOf(UnexpectedVersion.class);
  }

  @Test
  void shouldFailOnEmptyFile() throws IOException {
    // given
    final var tmp = Files.createTempDirectory("topology");
    final var topologyFile = tmp.resolve("topology.meta");
    final var serializer = new ProtoBufSerializer();

    // when
    Files.createFile(topologyFile);

    // then
    Assertions.assertThatCode(() -> PersistedClusterConfiguration.ofFile(topologyFile, serializer))
        .isInstanceOf(MissingHeader.class);
  }

  @Test
  void shouldFailOnMissingHeader() throws IOException {
    // given
    final var tmp = Files.createTempDirectory("topology");
    final var topologyFile = tmp.resolve("topology.meta");
    final var serializer = new ProtoBufSerializer();

    // when
    Files.write(
        topologyFile, new byte[] {1, 2}, StandardOpenOption.WRITE, StandardOpenOption.CREATE);

    // then
    Assertions.assertThatCode(() -> PersistedClusterConfiguration.ofFile(topologyFile, serializer))
        .isInstanceOf(MissingHeader.class);
  }

  @Test
  void shouldFailOnChangedTopology() throws IOException {
    // given
    final var tmp = Files.createTempDirectory("topology");
    final var topologyFile = tmp.resolve("topology.meta");
    final var serializer = new ProtoBufSerializer();
    final var persistedClusterTopology =
        PersistedClusterConfiguration.ofFile(topologyFile, serializer);

    // two very similar topologies with a single bit different (2 vs 3)
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("1"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, partitionConfig))));
    final var changedTopology =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("1"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(3, partitionConfig))));

    persistedClusterTopology.update(initialTopology);

    // when -- keep the same header but change the body
    final var fileContent = Files.readAllBytes(topologyFile);
    final var newBody = serializer.encode(changedTopology);
    System.arraycopy(
        newBody,
        0,
        fileContent,
        PersistedClusterConfiguration.Header.HEADER_LENGTH,
        newBody.length);
    Files.write(topologyFile, fileContent, StandardOpenOption.WRITE);

    // then -- checksum mismatch is detected
    Assertions.assertThatCode(() -> PersistedClusterConfiguration.ofFile(topologyFile, serializer))
        .isInstanceOf(ChecksumMismatch.class);
  }

  @Test
  void shouldKeepPreviousConfigurationOnDiskWhenWriteFails() throws IOException {
    // given - a persisted configuration, and a write of the next one that fails
    final var tmp = Files.createTempDirectory("topology");
    final var topologyFile = tmp.resolve("topology.meta");
    final var serializer = new ProtoBufSerializer();
    final var previous =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("1"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, partitionConfig))));
    final var persisted = PersistedClusterConfiguration.ofFile(topologyFile, serializer);
    persisted.update(previous);
    // the new file cannot be created if a directory is in its place
    Files.createDirectory(tmp.resolve("topology.meta.tmp"));

    // when
    final var thrown =
        Assertions.catchThrowable(() -> persisted.update(ClusterConfiguration.uninitialized()));

    // then - neither used nor gossiped, and still on disk after a restart
    Assertions.assertThat(thrown).isInstanceOf(IOException.class);
    Assertions.assertThat(persisted.getConfiguration()).isEqualTo(previous);
    Assertions.assertThat(
            PersistedClusterConfiguration.ofFile(topologyFile, serializer).getConfiguration())
        .isEqualTo(previous);
  }

  @Test
  void shouldRewriteUnchangedConfigurationAfterFailedWrite() throws IOException {
    // given - the new file replaced the old one, but syncing the directory failed
    final var tmp = Files.createTempDirectory("topology");
    final var topologyFile = tmp.resolve("topology.meta");
    final var serializer = new ProtoBufSerializer();
    final var previous =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("1"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, partitionConfig))));
    final var persisted = PersistedClusterConfiguration.ofFile(topologyFile, serializer);
    persisted.update(previous);
    try (final var fileUtil = mockStatic(FileUtil.class, CALLS_REAL_METHODS)) {
      fileUtil
          .when(() -> FileUtil.flushDirectory(tmp))
          .thenThrow(new IOException("Input/output error"));
      Assertions.assertThatThrownBy(() -> persisted.update(ClusterConfiguration.uninitialized()))
          .isInstanceOf(IOException.class);
    }

    // when - updating to the configuration in memory, which is not the one in the file
    persisted.update(previous);

    // then
    Assertions.assertThat(
            PersistedClusterConfiguration.ofFile(topologyFile, serializer).getConfiguration())
        .isEqualTo(previous);
  }
}
