/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import java.time.Duration;

/**
 * When a publisher ends a segment: once its entries reach {@code maxBytes}, or {@code maxAge} after
 * its first entry, whichever comes first.
 */
public record CutPolicy(long maxBytes, Duration maxAge) {

  public CutPolicy {
    if (maxBytes < 1) {
      throw new IllegalArgumentException(
          "Expected a segment size of at least 1 byte, but got " + maxBytes);
    }
    if (maxAge.isNegative() || maxAge.isZero()) {
      throw new IllegalArgumentException("Expected a positive segment age, but got " + maxAge);
    }
  }

  /** A leader's: a segment every 5 seconds or so, of at most 16 MiB. */
  public static CutPolicy live() {
    return new CutPolicy(16L << 20, Duration.ofSeconds(5));
  }
}
