/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import io.camunda.zeebe.util.libc.LibC;
import jnr.ffi.Runtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helper class to format RocksDB options. */
final class RocksDbOptionsFormatter {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDbOptionsFormatter.class);

  static String format(final boolean value) {
    return String.valueOf(value);
  }

  static String format(final long value) {
    return String.valueOf(value);
  }

  static String format(final int value) {
    return String.valueOf(value);
  }

  /**
   * This will take a double value and format it using libc `sprintf`. This ensures that we use the
   * same formatting logic that RocksDB uses. If sprintf fails for any reason, it falls back to
   * regular Java String#format and logs a warning.
   *
   * @see <a href="https://github.com/facebook/rocksdb/issues/13841">facebook/rocksdb#13841</a>
   */
  static String format(final double value) {
    final var libC = LibC.instance();
    if (libC != null) {
      try {
        // Allocate a buffer for the formatted string
        // 64 bytes should be more than enough for any reasonable double formatting
        final var buffer = Runtime.getRuntime(libC).getMemoryManager().allocateDirect(64);
        final var bytesWritten = libC.sprintf(buffer, "%f", value);

        if (bytesWritten >= 0) {
          // Convert the C string to Java String
          return buffer.getString(0);
        } else {
          LOG.warn(
              "sprintf failed to format double value: {}, falling back to String.format", value);
        }
      } catch (final Exception e) {
        LOG.warn(
            "Exception occurred while using sprintf to format double value: {}, falling back to String.format",
            value,
            e);
      }
    }

    // Fallback to regular Java String.format
    return String.format("%f", value);
  }
}
