/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.zeebe.topology.PersistedClusterTopology.ChecksumMismatch;
import io.camunda.zeebe.topology.PersistedClusterTopology.MissingHeader;
import io.camunda.zeebe.topology.PersistedClusterTopology.UnexpectedVersion;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PersistedClusterTopologyTest {
  @Test
  void shouldDetectFileWithMoreDataThanExpected() throws IOException {
    // given
    final var tmp = Files.createTempDirectory("topology");
    final var topologyFile = tmp.resolve("topology.meta");
    final var serializer = new ProtoBufSerializer();
    final var persistedClusterTopology = PersistedClusterTopology.ofFile(topologyFile, serializer);
    persistedClusterTopology.update(ClusterTopology.init());

    // when
    Files.write(topologyFile, "more data than expected".getBytes(), StandardOpenOption.APPEND);

    // then
    Assertions.assertThatCode(() -> PersistedClusterTopology.ofFile(topologyFile, serializer))
        .isInstanceOf(ChecksumMismatch.class);
  }

  @Test
  void shouldDetectFileWithBrokenHeader() throws IOException {
    // given
    final var tmp = Files.createTempDirectory("topology");
    final var topologyFile = tmp.resolve("topology.meta");
    final var serializer = new ProtoBufSerializer();
    final var persistedClusterTopology = PersistedClusterTopology.ofFile(topologyFile, serializer);
    persistedClusterTopology.update(ClusterTopology.init());

    // when
    Files.write(topologyFile, "broken header".getBytes(), StandardOpenOption.WRITE);

    // then
    Assertions.assertThatCode(() -> PersistedClusterTopology.ofFile(topologyFile, serializer))
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
    Assertions.assertThatCode(() -> PersistedClusterTopology.ofFile(topologyFile, serializer))
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
    Assertions.assertThatCode(() -> PersistedClusterTopology.ofFile(topologyFile, serializer))
        .isInstanceOf(MissingHeader.class);
  }
}
