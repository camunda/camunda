/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

/** This class provides constant values for logstreams and engine. */
public final class StreamProcessingConstants {

  // Fragment size constants
  /** Minimum fragment size in bytes (4KB) */
  public static final int MINIMUM_FRAGMENT_SIZE = 1024 * 4;

  /** Default maximum fragment size in bytes (4MB) */
  public static final int DEFAULT_MAX_FRAGMENT_SIZE = 1024 * 1024 * 4;

  /** Conservative fragment size limit for metrics export (3MB) */
  public static final int CONSERVATIVE_FRAGMENT_SIZE_LIMIT = 1024 * 1024 * 3;

  // Record processing constants for chunking
  /** Default estimated bytes per entry when calculating chunk sizes */
  public static final int DEFAULT_BYTES_PER_ENTRY = 100;

  /** Number of bytes per long value */
  public static final int BYTES_PER_LONG = 8;

  /** Minimum chunk size for record splitting */
  public static final int MIN_CHUNK_SIZE = 1000;

  /**
   * Chunk size divisor to decide how many follow-ups we want at most for record dividing in chunks
   */
  public static final int CHUNK_SIZE_DIVISOR = 5;
}
