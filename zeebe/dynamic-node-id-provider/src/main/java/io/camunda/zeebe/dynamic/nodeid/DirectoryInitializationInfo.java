/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

/**
 * @param initializedAt epoch in milliseconds when the directory was initialized
 * @param initializedFrom the version from which this directory was initialized, or null if this is
 *     the first initialization
 */
public record DirectoryInitializationInfo(long initializedAt, Version initializedFrom) {
  public DirectoryInitializationInfo {
    if (initializedAt < 0L) {
      throw new IllegalArgumentException("initializedAt cannot be negative");
    }
  }

  static DirectoryInitializationInfo copiedFrom(final Version version) {
    return new DirectoryInitializationInfo(System.currentTimeMillis(), version);
  }
}
