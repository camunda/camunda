/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

public final class ByteValue {
  private static final int CONVERSION_FACTOR_KB = 1024;
  private static final int CONVERSION_FACTOR_MB = CONVERSION_FACTOR_KB * 1024;
  private static final int CONVERSION_FACTOR_GB = CONVERSION_FACTOR_MB * 1024;

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
