/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.libc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class LibCTest {

  @Test
  void shouldLoadSystemLibC() {
    assertThat(LibC.instance()).isNotNull();
  }

  @Test
  void shouldReuseSystemLibC() {
    // given
    final var first = LibC.instance();

    // when
    final var second = LibC.instance();

    // then
    assertThat(second).isSameAs(first);
  }

  @Test
  void shouldReturnNullWhenNotFound() {
    // given
    final var libraryName = "dzz";

    // when
    final var loaded = LibC.load(libraryName);

    // then
    assertThat(loaded).isNull();
  }
}
