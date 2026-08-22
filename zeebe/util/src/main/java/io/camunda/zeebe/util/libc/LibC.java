/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.libc;

import io.camunda.zeebe.util.Loggers;
import java.util.Map;
import jnr.ffi.LibraryLoader;
import jnr.ffi.LibraryOption;
import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.annotations.In;
import jnr.ffi.annotations.Out;
import jnr.ffi.types.off_t;
import org.jspecify.annotations.Nullable;

/**
 * Binds the calls we need from the system's C library (e.g. glibc, musl) to Java methods via
 * jnr-ffi.
 *
 * <p>All libc calls used anywhere in the broker belong on this one interface, because jnr-ffi binds
 * a library to an <em>interface</em>: a second interface means a second {@code dlopen} and a second
 * generated proxy, even for the same library. Keeping a single interface keeps us to a single load
 * per JVM, which is what {@link Holder} relies on.
 *
 * <p>Note that method names do not follow our conventions, but this is necessary here because the
 * names must match those of the C library.
 *
 * <p>See {@link #instance()}} for how to obtain an instance.
 */
@SuppressWarnings({"checkstyle:methodname", "java:S100"})
public interface LibC {

  int posix_fallocate(final @In int fd, final @In @off_t long offset, final @In @off_t long len);

  int sprintf(final @Out Pointer str, final @In String format, final Object... args);

  /**
   * Returns the shared instance of LibC bound to the system's C library. The library is bound once
   * per JVM, on first call.
   *
   * <p>If it failed to bind to the C library, this returns null.
   *
   * @return the shared instance of this library
   */
  static @Nullable LibC instance() {
    return Holder.INSTANCE;
  }

  private static LibC load() {
    return load(Platform.getNativePlatform().getStandardCLibraryName());
  }

  /**
   * Binds a fresh instance to the given library name, bypassing the shared instance. Production
   * code must use {@link #load()} instead: every call here is another unsynchronized load, which is
   * exactly what {@link Holder} exists to avoid.
   */
  static LibC load(final String libraryName) {
    try {
      return LibraryLoader.loadLibrary(
          LibC.class, Map.of(LibraryOption.LoadNow, true), libraryName);
    } catch (final UnsatisfiedLinkError e) {
      Loggers.FILE_LOGGER.warn(
          "Failed to load C library; any native calls will not be available", e);
      return null;
    }
  }

  /** Holds the process-wide {@link LibC} binding, loaded on first access. */
  final class Holder {
    static final LibC INSTANCE = load();

    private Holder() {}
  }
}
