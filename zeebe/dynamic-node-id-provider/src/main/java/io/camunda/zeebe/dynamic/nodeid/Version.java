/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import com.fasterxml.jackson.annotation.JsonValue;

public record Version(@JsonValue long version) {
  private static final Version ZERO = new Version(0L);

  public Version {
    if (version < 0) {
      throw new IllegalArgumentException("version must be non-negative");
    }
  }

  public static Version zero() {
    return ZERO;
  }

  public static Version of(final long version) {
    return new Version(version);
  }

  public Version next() {
    return new Version(version + 1);
  }
}
