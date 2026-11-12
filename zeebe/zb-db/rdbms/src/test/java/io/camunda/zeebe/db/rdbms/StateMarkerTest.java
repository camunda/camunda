/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StateMarkerTest {

  @TempDir private Path directory;

  @Test
  void shouldReadWhatWasWritten() {
    // given
    final var marker = new StateMarker(3, UUID.randomUUID().toString());

    // when
    marker.writeTo(directory.resolve("new"));

    // then
    assertThat(StateMarker.readFrom(directory.resolve("new"))).isEqualTo(marker);
    assertThat(StateMarker.readIfPresent(directory.resolve("new"))).contains(marker);
  }

  @Test
  void shouldFindNoMarkerInADirectoryWithoutOne() {
    // when / then
    assertThat(StateMarker.readIfPresent(directory)).isEmpty();
    assertThat(StateMarker.readIfPresent(directory.resolve("missing"))).isEmpty();
  }

  @Test
  void shouldRejectAMarkerWithAMissingValue() throws Exception {
    // given
    Files.writeString(directory.resolve(StateMarker.FILE_NAME), "version=2\npartitionId=1\n");

    // when / then
    assertThatThrownBy(() -> StateMarker.readFrom(directory))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stateId");
  }

  @Test
  void shouldRejectAMarkerWithAValueThatIsNotANumber() throws Exception {
    // given
    Files.writeString(
        directory.resolve(StateMarker.FILE_NAME),
        "version=2\npartitionId=one\nstateId=" + UUID.randomUUID() + "\n");

    // when / then
    assertThatThrownBy(() -> StateMarker.readFrom(directory))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not a number");
  }
}
