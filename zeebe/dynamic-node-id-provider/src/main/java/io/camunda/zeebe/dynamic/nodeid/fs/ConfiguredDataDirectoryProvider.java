/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.fs;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * A data directory provider that returns the configured data directory as-is without any
 * modification.
 */
public class ConfiguredDataDirectoryProvider implements DataDirectoryProvider {

  @Override
  public CompletableFuture<Path> initialize(final Path baseDataDirectory) {
    return CompletableFuture.completedFuture(baseDataDirectory);
  }
}
