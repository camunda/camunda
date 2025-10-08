/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy that uses the pre-configured data directory directly without versioning or copying. This
 * approach simply ensures the directory exists and creates an initialization marker if needed.
 */
public class UsePreConfiguredDirectoryStrategy implements DataDirectoryInitializationStrategy {
  private static final Logger LOG =
      LoggerFactory.getLogger(UsePreConfiguredDirectoryStrategy.class);
  private static final String DIRECTORY_INITIALIZED_FILE = "directory-initialized.json";
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Path initializeDataDirectory(
      final String rootDirectory, final int nodeId, final long currentNodeVersion)
      throws IOException {

    final var rootDirectoryPath = Path.of(rootDirectory);
    LOG.info("Using pre-configured data directory at {}", rootDirectory);

    // Create the directory if it doesn't exist
    Files.createDirectories(rootDirectoryPath);

    return rootDirectoryPath;
  }

  @Override
  public String getStrategyName() {
    return "UsePreConfiguredDirectory";
  }

  private boolean isDirectoryInitialized(final Path directory) {
    final Path initFile = directory.resolve(DIRECTORY_INITIALIZED_FILE);
    return Files.exists(initFile) && Files.isRegularFile(initFile);
  }

  private void writeDirectoryInitializedFile(final Path dataDirectory) throws IOException {
    final DirectoryInitializationInfo initInfo = DirectoryInitializationInfo.createdEmpty();

    final Path initFile = dataDirectory.resolve(DIRECTORY_INITIALIZED_FILE);
    objectMapper.writeValue(initFile.toFile(), initInfo);
    LOG.info("Written directory initialization file to {}", initFile);
  }
}
