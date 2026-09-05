/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class MigrationStatusPayloadTest {

  @ParameterizedTest
  @EnumSource(MigrationStatusCode.class)
  void shouldRoundTripEveryCode(final MigrationStatusCode code) {
    // given
    final var status = new PartitionMigrationStatus(code, "some detail text");

    // when
    final var decoded = MigrationStatusPayload.decode(MigrationStatusPayload.encode(status));

    // then
    assertThat(decoded).isEqualTo(status);
  }

  @Test
  void shouldRoundTripEmptyDetail() {
    // given
    final var status = new PartitionMigrationStatus(MigrationStatusCode.MIGRATED, "");

    // when
    final var decoded = MigrationStatusPayload.decode(MigrationStatusPayload.encode(status));

    // then
    assertThat(decoded).isEqualTo(status);
  }

  @Test
  void shouldPreserveNewlinesInDetail() {
    // given - the detail is everything after the first delimiter, so it may contain more of them
    final var status =
        new PartitionMigrationStatus(MigrationStatusCode.UNKNOWN, "line one\nline two");

    // when
    final var decoded = MigrationStatusPayload.decode(MigrationStatusPayload.encode(status));

    // then
    assertThat(decoded).isEqualTo(status);
  }
}
