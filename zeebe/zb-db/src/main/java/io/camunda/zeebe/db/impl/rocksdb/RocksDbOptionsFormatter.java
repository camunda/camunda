/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.annotations.In;
import jnr.ffi.annotations.Out;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helper class to format RocksDB options. */
final class RocksDbOptionsFormatter {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDbOptionsFormatter.class);
  private static LibC libC;
  private static Runtime runtime;
  private static boolean libCUnavailable = false;

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
    if (ensureLibCIsAvailable()) {
      try {
        // Allocate a buffer for the formatted string
        // 64 bytes should be more than enough for any reasonable double formatting
        final var buffer = runtime.getMemoryManager().allocateDirect(64);
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
    return String.format("%,f", value);
  }

  private static boolean ensureLibCIsAvailable() {
    if (libC != null && runtime != null) {
      return true;
    }
    if (libCUnavailable) {
      return false;
    }
    try {
      libC = LibraryLoader.create(LibC.class).load("c");
      runtime = Runtime.getRuntime(libC);
    } catch (final Exception e) {
      libCUnavailable = true;
      LOG.warn("Failed to load libc for sprintf formatting, will fall back to String.format", e);
    }
    return true;
  }

  /** Interface to access libc functions via JNR-FFI. */
  public interface LibC {
    /**
     * sprintf function from libc.
     *
     * @param str output buffer
     * @param format format string
     * @param value the double value to format
     * @return number of characters written (excluding null terminator)
     */
    int sprintf(@Out Pointer str, @In String format, double value);
  }
}
