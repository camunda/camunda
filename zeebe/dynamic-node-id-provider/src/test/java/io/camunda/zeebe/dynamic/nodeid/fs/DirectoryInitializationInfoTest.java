/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.dynamic.nodeid.Version;
import org.junit.jupiter.api.Test;

class DirectoryInitializationInfoTest {

  @Test
  void shouldThrowExceptionWhenInitializedAtIsNegative() {
    // given
    final var version = Version.of(1);
    final var initializedAt = -1L;

    // when/then
    assertThatThrownBy(() -> new DirectoryInitializationInfo(initializedAt, version, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("initializedAt cannot be negative");
  }

  @Test
  void shouldThrowExceptionWhenVersionIsNull() {
    // given
    final var initializedAt = 1234567890L;

    // when/then
    assertThatThrownBy(() -> new DirectoryInitializationInfo(initializedAt, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("version cannot be null");
  }

  @Test
  void shouldCreateValidDirectoryInitializationInfo() {
    // given
    final var initializedAt = 1234567890L;
    final var version = Version.of(1);
    final var initializedFrom = Version.of(0);

    // when
    final var info = new DirectoryInitializationInfo(initializedAt, version, initializedFrom);

    // then
    assertThat(info.initializedAt()).isEqualTo(initializedAt);
    assertThat(info.version()).isEqualTo(version);
    assertThat(info.initializedFrom()).isEqualTo(initializedFrom);
  }

  @Test
  void shouldAllowNullInitializedFrom() {
    // given
    final var initializedAt = 1234567890L;
    final var version = Version.of(1);

    // when
    final var info = new DirectoryInitializationInfo(initializedAt, version, null);

    // then
    assertThat(info.initializedFrom()).isNull();
  }
}
