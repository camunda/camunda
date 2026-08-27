/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.fs;

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
  static final LibC INSTANCE =
      LibC.ofNativeLibrary(Platform.getNativePlatform().getStandardCLibraryName());

  private LibCHolder() {}
}
