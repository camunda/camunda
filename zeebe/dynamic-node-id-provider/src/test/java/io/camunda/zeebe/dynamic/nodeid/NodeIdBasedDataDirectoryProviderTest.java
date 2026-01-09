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

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodeIdBasedDataDirectoryProviderTest {

  @TempDir Path tempDir;

  @Test
  void shouldAppendNodeIdToBaseDirectory() {
    // given
    final int nodeId = 5;
    final NodeIdProvider nodeIdProvider = mock(NodeIdProvider.class);
    when(nodeIdProvider.currentNodeInstance()).thenReturn(new NodeInstance(nodeId, Version.of(3L)));

    final Path baseDirectory = tempDir.resolve("base");
    final DataDirectoryProvider initializer = new NodeIdBasedDataDirectoryProvider(nodeIdProvider);

    // when
    final CompletableFuture<Path> result = initializer.initialize(baseDirectory, false);

    // then
    assertThat(result).isCompleted();
    assertThat(result.join()).isEqualTo(baseDirectory.resolve(String.valueOf(nodeId)));
  }

  @Test
  void shouldFailWhenNodeInstanceIsNull() {
    // given
    final NodeIdProvider nodeIdProvider = mock(NodeIdProvider.class);
    when(nodeIdProvider.currentNodeInstance()).thenReturn(null);

    final Path baseDirectory = tempDir.resolve("base");
    final DataDirectoryProvider initializer = new NodeIdBasedDataDirectoryProvider(nodeIdProvider);

    // when
    final CompletableFuture<Path> result = initializer.initialize(baseDirectory, false);

    // then
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Node instance is not available");
  }
}
