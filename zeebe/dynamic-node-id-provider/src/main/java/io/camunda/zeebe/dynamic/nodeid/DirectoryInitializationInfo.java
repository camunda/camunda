/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

/**
 * @param initialized epoch in milliseconds
 * @param initializedFrom nullable if it's the first time it's been initialized
 */
record DirectoryInitializationInfo(long initialized, Version initializedFrom) {
  public DirectoryInitializationInfo {
    if (initialized < 0L) {
      throw new IllegalArgumentException("initialized cannot be negative");
    }
  }

  static DirectoryInitializationInfo copiedFrom(final Version version) {
    return new DirectoryInitializationInfo(System.currentTimeMillis(), version);
  }
}
