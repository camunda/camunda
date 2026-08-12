/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.record.value.StorageOrdinalKeyRelated;
import org.junit.jupiter.api.Test;

class TargetIndexLocatorTest {
  private final TargetIndexLocator targetIndexLocator = new TargetIndexLocator();

  @Test
  void shouldLocateOrdinalIndexFromOrdinal() {
    final var ordinalRelated = mock(StorageOrdinalKeyRelated.class);
    when(ordinalRelated.getStorageOrdinalKey()).thenReturn(5);

    final var targetIndex = targetIndexLocator.locateOrdinalIndex("test-index", ordinalRelated);
    assertThat(targetIndex.name()).isEqualTo("test-indexord00005");
  }

  @Test
  void shouldLocateOrdinalIndexFromDefaultOrdinal() {
    final var ordinalRelated = mock(StorageOrdinalKeyRelated.class);
    when(ordinalRelated.getStorageOrdinalKey()).thenReturn(0);

    final var targetIndex = targetIndexLocator.locateOrdinalIndex("test-index", ordinalRelated);
    assertThat(targetIndex.name()).isEqualTo("test-index");
  }

  @Test
  void shouldLocateOrdinalIndexFromNegativeOrdinal() {
    final var ordinalRelated = mock(StorageOrdinalKeyRelated.class);
    when(ordinalRelated.getStorageOrdinalKey()).thenReturn(-1);

    final var targetIndex = targetIndexLocator.locateOrdinalIndex("test-index", ordinalRelated);
    assertThat(targetIndex.name()).isEqualTo("test-index");
  }
}
