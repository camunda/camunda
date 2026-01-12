/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreConfiguredDataDirectoryProviderTest {

  @TempDir Path tempDir;

  @Test
  void shouldReturnConfiguredDirectory() {
    // given
    final Path dataDirectory = tempDir.resolve("data");
    final DataDirectoryProvider initializer = new ConfiguredDataDirectoryProvider();

    // when
    final CompletableFuture<Path> result = initializer.initialize(dataDirectory);

    // then
    assertThat(result).isCompleted();
    assertThat(result.join()).isEqualTo(dataDirectory);
  }
}
