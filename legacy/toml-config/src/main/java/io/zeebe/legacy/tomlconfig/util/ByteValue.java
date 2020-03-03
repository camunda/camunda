/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.legacy.tomlconfig.util;

@Deprecated(since = "0.23.0-alpha2", forRemoval = true)
/* Kept in order to be able to offer a migration path for old configurations.
 */
public final class ByteValue {
  private static final int CONVERSION_FACTOR_KB = 1024;
  private static final int CONVERSION_FACTOR_MB = CONVERSION_FACTOR_KB * 1024;
  private static final int CONVERSION_FACTOR_GB = CONVERSION_FACTOR_MB * 1024;

  private final ByteUnit unit;
  private final long value;

  @Deprecated(forRemoval = true, since = "0.23.0-alpha2")
  /* Should be replaced when an alternative is found. For now, please refrain
  from using this class outside configuration and use a long (size in bytes) instead*/
  protected ByteValue(final long value, final ByteUnit unit) {
    this.value = value;
    this.unit = unit;
  }

  @Deprecated
  public ByteUnit getUnit() {
    return unit;
  }

  @Deprecated
  public long getValue() {
    return value;
  }

  @Deprecated
  public long toBytes() {
    return unit.toBytes(value);
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
  public boolean equals(final Object obj) {
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

  /**
   * Converts the {@code value} kilobytes into bytes
   *
   * @param value value in kilobytes
   * @return {@code value} converted into bytes
   */
  public static long ofKilobytes(final long value) {
    return value * CONVERSION_FACTOR_KB;
  }

  /**
   * Converts the {@code value} megabytes into bytes
   *
   * @param value value in megabytes
   * @return {@code value} converted into bytes
   */
  public static long ofMegabytes(final long value) {
    return value * CONVERSION_FACTOR_MB;
  }

  /**
   * Converts the {@code value} gigabytes into bytes
   *
   * @param value value in gigabytes
   * @return {@code value} converted into bytes
   */
  public static long ofGigabytes(final long value) {
    return value * CONVERSION_FACTOR_GB;
  }
}
