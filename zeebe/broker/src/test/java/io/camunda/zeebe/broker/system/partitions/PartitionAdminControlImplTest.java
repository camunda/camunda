/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

final class PartitionAdminControlImplTest {

  @Test
  void shouldTakeASnapshot() {
    // given
    final var snapshotDirector = mock(AsyncSnapshotDirector.class);
    final var adminControl = adminControlWith(snapshotDirector);

    // when
    adminControl.triggerSnapshot();

    // then
    verify(snapshotDirector).forceSnapshot();
  }

  @Test
  void shouldSkipTheSnapshotWithoutASnapshotDirector() {
    // given
    final var adminControl = adminControlWith(null);

    // when - then
    assertThatCode(adminControl::triggerSnapshot).doesNotThrowAnyException();
  }

  private static PartitionAdminControlImpl adminControlWith(
      final @Nullable AsyncSnapshotDirector snapshotDirector) {
    return new PartitionAdminControlImpl(
        () -> null,
        () -> null,
        () -> snapshotDirector,
        () -> null,
        () -> null,
        () -> null,
        () -> false);
  }
}
