/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.legacy.tomlconfig.util;

import java.util.Arrays;

@Deprecated(since = "0.23.0-alpha2", forRemoval = true)
/* Kept in order to be able to offer a migration path for old configurations.
 */
public enum ByteUnit {
  BYTES(0, ""),
  KILOBYTES(1, "K"),
  MEGABYTES(2, "M"),
  GIGABYTES(3, "G");

  private final double unitFactor;
  private final String metric;

  ByteUnit(final double factor, final String metric) {
    this.unitFactor = factor;
    this.metric = metric;
  }

  public long toBytes(final long value) {
    final double methodFactor = 0;
    return calculateValue(value, methodFactor);
  }

  public long toKilobytes(final long value) {
    final double methodFactor = 1;
    return calculateValue(value, methodFactor);
  }

  public long toMegabytes(final long value) {
    final double methodFactor = 2;
    return calculateValue(value, methodFactor);
  }

  public long toGigabytes(final long value) {
    final double methodFactor = 3;
    return calculateValue(value, methodFactor);
  }

  private long calculateValue(final long value, final double methodFactor) {
    if (unitFactor < methodFactor) {
      return value / (long) Math.pow(1024, methodFactor - unitFactor);
    } else if (unitFactor == methodFactor) {
      return value;
    } else {
      return value * (long) Math.pow(1024, unitFactor - methodFactor);
    }
  }

  public static ByteUnit getUnit(final String unitString) {
    return Arrays.stream(ByteUnit.values())
        .filter(unit -> unit.metric.equalsIgnoreCase(unitString))
        .findAny()
        .orElse(BYTES);
  }

  public String metric() {
    return metric;
  }
}
