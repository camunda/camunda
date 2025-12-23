/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodeIdBasedDataDirectoryProviderTest {

  @TempDir Path tempDir;

  @Test
  void shouldReturnNodeIdAndVersionedDirectory() {
    // given
    final int nodeId = 5;
    final long nodeVersion = 3L;

    final NodeIdProvider nodeIdProvider = mock(NodeIdProvider.class);
    when(nodeIdProvider.currentNodeInstance())
        .thenReturn(new NodeInstance(nodeId, Version.of(nodeVersion)));

    final Path rootDirectory = tempDir.resolve("root");
    final DataDirectoryProvider initializer = new NodeIdBasedDataDirectoryProvider(nodeIdProvider);

    // when
    final CompletableFuture<Path> result = initializer.initialize(rootDirectory);

    // then
    assertThat(result).isCompleted();
    assertThat(result.join()).isEqualTo(rootDirectory.resolve("node-5").resolve("v3"));
    assertThat(result.join().resolve("directory-initialized.json")).exists().isRegularFile();
  }

  @Test
  void shouldCopyFromPreviousInitializedVersion() throws Exception {
    // given
    final int nodeId = 2;
    final NodeIdProvider nodeIdProvider = mock(NodeIdProvider.class);
    when(nodeIdProvider.currentNodeInstance()).thenReturn(new NodeInstance(nodeId, Version.of(4L)));

    final Path rootDirectory = tempDir.resolve("root");

    final Path previous = rootDirectory.resolve("node-2").resolve("v3").resolve("raft-partition");
    final Path previousPartition = previous.resolve("partitions").resolve("1");
    Files.createDirectories(previousPartition.resolve("snapshots").resolve("snap-1"));
    Files.writeString(
        previousPartition.resolve("snapshots").resolve("snap-1").resolve("file.bin"), "abc");
    Files.writeString(previousPartition.resolve("atomix-partition-1.meta"), "meta");
    Files.writeString(previousPartition.resolve("atomix-partition-1.conf"), "conf");
    Files.writeString(previousPartition.resolve("atomix-partition-1-1.log"), "log");
    Files.createDirectories(previousPartition.resolve("runtime"));
    Files.writeString(previousPartition.resolve("runtime").resolve("ignore.txt"), "ignore");
    Files.writeString(
        rootDirectory.resolve("node-2").resolve("v3").resolve("directory-initialized.json"),
        "{}\n");

    final DataDirectoryProvider initializer = new NodeIdBasedDataDirectoryProvider(nodeIdProvider);

    // when
    final Path newDirectory = initializer.initialize(rootDirectory).join();

    // then
    assertThat(newDirectory).isEqualTo(rootDirectory.resolve("node-2").resolve("v4"));

    final Path copiedPartition =
        newDirectory.resolve("raft-partition").resolve("partitions").resolve("1");

    assertThat(copiedPartition.resolve("atomix-partition-1.meta")).exists();
    assertThat(copiedPartition.resolve("atomix-partition-1.conf")).exists();
    assertThat(copiedPartition.resolve("atomix-partition-1-1.log")).exists();
    assertThat(copiedPartition.resolve("runtime")).doesNotExist();

    final var sourceSnapshot =
        previousPartition.resolve("snapshots").resolve("snap-1").resolve("file.bin");
    final var copiedSnapshot =
        copiedPartition.resolve("snapshots").resolve("snap-1").resolve("file.bin");
    assertThat(copiedSnapshot).exists();

    // hardlink if possible (same file key), otherwise at least identical content
    final var sourceKey = Files.readAttributes(sourceSnapshot, BasicFileAttributes.class).fileKey();
    final var targetKey = Files.readAttributes(copiedSnapshot, BasicFileAttributes.class).fileKey();
    if (sourceKey != null && targetKey != null) {
      assertThat(sourceKey).isEqualTo(targetKey);
    } else {
      assertThat(Files.readString(copiedSnapshot)).isEqualTo("abc");
    }
  }

  @Test
  void shouldFailWhenNodeInstanceIsNull() {
    // given
    final NodeIdProvider nodeIdProvider = mock(NodeIdProvider.class);
    when(nodeIdProvider.currentNodeInstance()).thenReturn(null);

    final Path baseDirectory = tempDir.resolve("base");
    final DataDirectoryProvider initializer = new NodeIdBasedDataDirectoryProvider(nodeIdProvider);

    // when
    final CompletableFuture<Path> result = initializer.initialize(baseDirectory);

    // then
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Node instance is not available");
  }
}
