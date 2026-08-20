/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.fs;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.dynamic.nodeid.Version;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VersionedDirectoryLayoutTest {

  private static final Version VERSION = Version.of(1);
  @TempDir Path tempDir;

  private VersionedDirectoryLayout layout;
  private Path initFile;

  @BeforeEach
  void setUp() throws IOException {
    layout = new VersionedDirectoryLayout(tempDir, ObjectMapperInstance.INSTANCE);
    final var versionDir = layout.resolveVersionDirectory(VERSION);
    Files.createDirectories(versionDir);
    initFile = layout.initializationFilePath(VERSION);
  }

  @Test
  void shouldNotMarkDirectoryAsInitializedWhenJsonIsInWrongFolder() throws IOException {
    // given - directory v1 but JSON contains version 2

    final var info = new DirectoryInitializationInfo(1234567890L, Version.of(2), null);
    final var jsonContent = ObjectMapperInstance.INSTANCE.writeValueAsString(info);
    Files.writeString(initFile, jsonContent);

    // when
    final var isInitialized = layout.isDirectoryInitialized(VERSION);

    // then
    assertThat(isInitialized).isFalse();
  }
}
