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

import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import org.junit.jupiter.api.Test;

class TargetIndexLocatorTest {
  private final TargetIndexLocator targetIndexLocator = new TargetIndexLocator();

  @Test
  void shouldLocateOrdinalIndexFromOrdinal() {
    final var ordinalRelated = mock(StorageOrdinalRelated.class);
    when(ordinalRelated.getStorageOrdinal()).thenReturn(5);

    final var targetIndex = targetIndexLocator.locateOrdinalIndex("test-index", ordinalRelated);
    assertThat(targetIndex.name()).isEqualTo("test-indexord00005");
  }

  @Test
  void shouldLocateOrdinalIndexFromDefaultOrdinal() {
    final var ordinalRelated = mock(StorageOrdinalRelated.class);
    when(ordinalRelated.getStorageOrdinal()).thenReturn(0);

    final var targetIndex = targetIndexLocator.locateOrdinalIndex("test-index", ordinalRelated);
    assertThat(targetIndex.name()).isEqualTo("test-index");
  }

  @Test
  void shouldLocateOrdinalIndexFromNegativeOrdinal() {
    final var ordinalRelated = mock(StorageOrdinalRelated.class);
    when(ordinalRelated.getStorageOrdinal()).thenReturn(-1);

    final var targetIndex = targetIndexLocator.locateOrdinalIndex("test-index", ordinalRelated);
    assertThat(targetIndex.name()).isEqualTo("test-index");
  }

  @Test
  void shouldLocateHistoryDeletionOrdinalIndexFromResourceOrdinal() {
    // given
    final var historyDeletion = mock(HistoryDeletionRecordValue.class);
    when(historyDeletion.getResourceStorageOrdinal()).thenReturn(5);

    // when
    final var targetIndex =
        targetIndexLocator.locateHistoryDeletionOrdinalIndex("test-index", historyDeletion);

    // then
    assertThat(targetIndex.name()).isEqualTo("test-indexord00005");
  }

  @Test
  void shouldLocateHistoryDeletionOrdinalIndexFromDefaultResourceOrdinal() {
    // given
    final var historyDeletion = mock(HistoryDeletionRecordValue.class);
    when(historyDeletion.getResourceStorageOrdinal()).thenReturn(0);

    // when
    final var targetIndex =
        targetIndexLocator.locateHistoryDeletionOrdinalIndex("test-index", historyDeletion);

    // then
    assertThat(targetIndex.name()).isEqualTo("test-index");
  }

  @Test
  void shouldLocateHistoryDeletionOrdinalIndexFromNegativeResourceOrdinal() {
    // given
    final var historyDeletion = mock(HistoryDeletionRecordValue.class);
    when(historyDeletion.getResourceStorageOrdinal()).thenReturn(-1);

    // when
    final var targetIndex =
        targetIndexLocator.locateHistoryDeletionOrdinalIndex("test-index", historyDeletion);

    // then
    assertThat(targetIndex.name()).isEqualTo("test-index");
  }

  @Test
  void shouldLocateHistoryDeletionEntityIndexFromResourceOrdinal() {
    // given
    final var entity = new HistoryDeletionEntity().setResourceStorageOrdinal(5);

    // when
    final var targetIndex =
        targetIndexLocator.locateHistoryDeletionOrdinalIndex("test-index", entity);

    // then
    assertThat(targetIndex.name()).isEqualTo("test-indexord00005");
  }

  @Test
  void shouldLocateHistoryDeletionEntityIndexFromDefaultResourceOrdinal() {
    // given
    final var entity = new HistoryDeletionEntity().setResourceStorageOrdinal(0);

    // when
    final var targetIndex =
        targetIndexLocator.locateHistoryDeletionOrdinalIndex("test-index", entity);

    // then
    assertThat(targetIndex.name()).isEqualTo("test-index");
  }

  @Test
  void shouldLocateHistoryDeletionEntityIndexFromMissingResourceOrdinal() {
    // given
    final var entity = new HistoryDeletionEntity();

    // when
    final var targetIndex =
        targetIndexLocator.locateHistoryDeletionOrdinalIndex("test-index", entity);

    // then
    assertThat(targetIndex.name()).isEqualTo("test-index");
  }
}
