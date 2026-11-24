/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.VersionInfo;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import org.junit.jupiter.api.Test;

final class UnprocessableRecordTest {

  private static final VersionInfo CURRENT = RecordMetadata.CURRENT_BROKER_VERSION;

  @Test
  void shouldBeFromNewerVersionWhenMajorVersionIsHigher() {
    // given
    final var version =
        new VersionInfo(
            CURRENT.getMajorVersion() + 1, CURRENT.getMinorVersion(), CURRENT.getPatchVersion());

    // when
    final var record = recordWrittenBy(version);

    // then
    assertThat(record.isFromNewerVersion()).isTrue();
  }

  @Test
  void shouldBeFromNewerVersionWhenMinorVersionIsHigher() {
    // given
    final var version =
        new VersionInfo(
            CURRENT.getMajorVersion(), CURRENT.getMinorVersion() + 1, CURRENT.getPatchVersion());

    // when
    final var record = recordWrittenBy(version);

    // then
    assertThat(record.isFromNewerVersion()).isTrue();
  }

  @Test
  void shouldBeFromNewerVersionWhenPatchVersionIsHigher() {
    // given
    final var version =
        new VersionInfo(
            CURRENT.getMajorVersion(), CURRENT.getMinorVersion(), CURRENT.getPatchVersion() + 1);

    // when
    final var record = recordWrittenBy(version);

    // then
    assertThat(record.isFromNewerVersion()).isTrue();
  }

  @Test
  void shouldNotBeFromNewerVersionWhenVersionIsCurrent() {
    // when
    final var record = recordWrittenBy(CURRENT);

    // then
    assertThat(record.isFromNewerVersion()).isFalse();
  }

  @Test
  void shouldNotBeFromNewerVersionWhenMinorVersionIsLowerDespiteHigherPatchVersion() {
    // given
    final var version =
        new VersionInfo(
            CURRENT.getMajorVersion(),
            CURRENT.getMinorVersion() - 1,
            CURRENT.getPatchVersion() + 1);

    // when
    final var record = recordWrittenBy(version);

    // then
    assertThat(record.isFromNewerVersion()).isFalse();
  }

  private static UnprocessableRecord recordWrittenBy(final VersionInfo version) {
    return new UnprocessableRecord(1L, RecordType.COMMAND, ValueType.SBE_UNKNOWN, version);
  }
}
