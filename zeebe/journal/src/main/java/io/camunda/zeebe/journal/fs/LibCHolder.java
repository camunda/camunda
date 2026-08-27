/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.fs;

import io.camunda.zeebe.journal.fs.LibC.InvalidLibC;
import io.camunda.zeebe.util.Loggers;
import java.util.Map;
import jnr.ffi.LibraryLoader;
import jnr.ffi.LibraryOption;
import jnr.ffi.Platform;

/**
 * Lazy-init holder for the shared {@link LibC} instance; the JVM guarantees this class's static
 * initialization runs at most once, under the class-init lock. See {@link LibC#ofNativeLibrary()}
 * for why the native bind must happen at most once per JVM.
 *
 * <p>Deliberately a package-private top-level class: a member type nested in the {@link LibC}
 * interface would be implicitly {@code public} (JLS 9.5) and leak an initialization detail into the
 * public API.
 */
final class LibCHolder {
  static final LibC INSTANCE = bind(Platform.getNativePlatform().getStandardCLibraryName());

  private LibCHolder() {}

  /**
   * Binds a fresh {@link LibC} instance to the given library, falling back to {@link InvalidLibC}
   * if the library cannot be linked. Production code must use the shared {@link #INSTANCE} instead;
   * see {@link LibC#ofNativeLibrary()}.
   */
  static LibC bind(final String libraryName) {
    try {
      return LibraryLoader.loadLibrary(
          LibC.class, Map.of(LibraryOption.LoadNow, true), libraryName);
    } catch (final UnsatisfiedLinkError e) {
      Loggers.FILE_LOGGER.warn(
          "Failed to load C library; any native calls will not be available", e);
      return new InvalidLibC();
    }
  }
}
