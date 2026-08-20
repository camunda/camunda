/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.zeebe.journal.fs.LibC.InvalidLibC;
import org.junit.jupiter.api.Test;

public class LibCTest {

  @Test
  void shouldLoadSystemLibC() {
    assertThatNoException().isThrownBy(LibC::ofNativeLibrary);
  }

  @Test
  void shouldReturnInvalidLibCWhenNotFound() {
    // given
    final var libraryName = "dzz";
    // when
    final var loaded = LibC.ofNativeLibrary(libraryName);
    // then
    assertThat(loaded).isInstanceOf(InvalidLibC.class);
  }
}
