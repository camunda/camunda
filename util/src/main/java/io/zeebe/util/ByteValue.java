/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import static io.zeebe.util.ByteUnit.BYTES;
import static io.zeebe.util.ByteUnit.KILOBYTES;
import static io.zeebe.util.ByteUnit.MEGABYTES;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ByteValue {
  private static final Pattern PATTERN =
      Pattern.compile("(\\d+)([K|M|G]?)", Pattern.CASE_INSENSITIVE);

  private final ByteUnit unit;
  private final long value;

  public ByteValue(long value, ByteUnit unit) {
    this.value = value;
    this.unit = unit;
  }

  public ByteValue(String humanReadable) {
    final Matcher matcher = PATTERN.matcher(humanReadable);

    if (!matcher.matches()) {
      final String err =
          String.format(
              "Illegal byte value '%s'. Must match '%s'. Valid examples: 100M, 4K, ...",
              humanReadable, PATTERN.pattern());
      throw new IllegalArgumentException(err);
    }

    final String valueString = matcher.group(1);
    value = Long.parseLong(valueString);

    final String unitString = matcher.group(2).toUpperCase();

    unit = ByteUnit.getUnit(unitString);
  }

  public static ByteValue ofBytes(long value) {
    return new ByteValue(value, ByteUnit.BYTES);
  }

  public static ByteValue ofKilobytes(long value) {
    return new ByteValue(value, ByteUnit.KILOBYTES);
  }

  public static ByteValue ofMegabytes(long value) {
    return new ByteValue(value, ByteUnit.MEGABYTES);
  }

  public static ByteValue ofGigabytes(long value) {
    return new ByteValue(value, ByteUnit.GIGABYTES);
  }

  public ByteUnit getUnit() {
    return unit;
  }

  public long getValue() {
    return value;
  }

  public long toBytes() {
    return unit.toBytes(value);
  }

  public ByteValue toBytesValue() {
    return new ByteValue(unit.toBytes(value), BYTES);
  }

  public ByteValue toKilobytesValue() {
    return new ByteValue(unit.toKilobytes(value), KILOBYTES);
  }

  public ByteValue toMegabytesValue() {
    return new ByteValue(unit.toMegabytes(value), MEGABYTES);
  }

  public ByteValue toGigabytesValue() {
    return new ByteValue(unit.toGigabytes(value), ByteUnit.GIGABYTES);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((unit == null) ? 0 : unit.hashCode());
    result = prime * result + (int) (value ^ (value >>> 32));
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ByteValue other = (ByteValue) obj;
    if (unit != other.unit) {
      return false;
    }
    if (value != other.value) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return String.format("%d%s", value, unit.metric());
  }
}
