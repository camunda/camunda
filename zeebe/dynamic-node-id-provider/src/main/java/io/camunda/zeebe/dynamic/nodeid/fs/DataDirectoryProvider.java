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

public interface DataDirectoryProvider {

  /**
   * Computes and returns the data directory path based on the base directory. The implementation
   * may also perform initialization of the data directory.
   */
  CompletableFuture<Path> initialize(final Path baseDirectory);
}
