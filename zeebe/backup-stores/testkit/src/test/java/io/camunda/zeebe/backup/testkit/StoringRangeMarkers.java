/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.api.BackupRangeMarker.Deletion;
import io.camunda.zeebe.backup.api.BackupRangeMarker.End;
import io.camunda.zeebe.backup.api.BackupRangeMarker.Start;
import io.camunda.zeebe.backup.api.BackupStore;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public interface StoringRangeMarkers {
  BackupStore getStore();

  @Test
  default void shouldReturnEmptyListWhenNoMarkersExist() {
    // given
    final var store = getStore();
    final int partitionId = 1;

    // when
    final var markers = store.rangeMarkers(partitionId).join();

    // then
    assertThat(markers).isEmpty();
  }

  @Test
  default void shouldStoreStartMarker() {
    // given
    final var store = getStore();
    final int partitionId = 1;
    final var marker = new Start(100L);

    // when
    assertThat(store.storeRangeMarker(partitionId, marker)).succeedsWithin(Duration.ofSeconds(10));

    // then
    final var markers = store.rangeMarkers(partitionId).join();
    assertThat(markers).containsExactly(marker);
  }

  @Test
  default void shouldStoreEndMarker() {
    // given
    final var store = getStore();
    final int partitionId = 1;
    final var marker = new End(200L);

    // when
    assertThat(store.storeRangeMarker(partitionId, marker)).succeedsWithin(Duration.ofSeconds(10));

    // then
    final var markers = store.rangeMarkers(partitionId).join();
    assertThat(markers).containsExactly(marker);
  }

  @Test
  default void shouldStoreDeletionMarker() {
    // given
    final var store = getStore();
    final int partitionId = 1;
    final var marker = new Deletion(300L);

    // when
    assertThat(store.storeRangeMarker(partitionId, marker)).succeedsWithin(Duration.ofSeconds(10));

    // then
    final var markers = store.rangeMarkers(partitionId).join();
    assertThat(markers).containsExactly(marker);
  }

  @Test
  default void shouldStoreMultipleMarkersForSamePartition() {
    // given
    final var store = getStore();
    final int partitionId = 1;
    final var startMarker = new Start(100L);
    final var endMarker = new End(200L);
    final var deletionMarker = new Deletion(150L);

    // when
    store.storeRangeMarker(partitionId, startMarker).join();
    store.storeRangeMarker(partitionId, endMarker).join();
    store.storeRangeMarker(partitionId, deletionMarker).join();

    // then
    final var markers = store.rangeMarkers(partitionId).join();
    assertThat(markers).containsExactlyInAnyOrder(startMarker, endMarker, deletionMarker);
  }

  @Test
  default void shouldStoreMarkersForDifferentPartitionsSeparately() {
    // given
    final var store = getStore();
    final int partition1 = 1;
    final int partition2 = 2;
    final var marker1 = new Start(100L);
    final var marker2 = new Start(200L);

    // when
    store.storeRangeMarker(partition1, marker1).join();
    store.storeRangeMarker(partition2, marker2).join();

    // then
    assertThat(store.rangeMarkers(partition1).join()).containsExactly(marker1);
    assertThat(store.rangeMarkers(partition2).join()).containsExactly(marker2);
  }

  @Test
  default void shouldOverwriteExistingMarker() {
    // given
    final var store = getStore();
    final int partitionId = 1;
    final var marker = new Start(100L);

    // when - store the same marker twice
    store.storeRangeMarker(partitionId, marker).join();
    assertThat(store.storeRangeMarker(partitionId, marker)).succeedsWithin(Duration.ofSeconds(10));

    // then - should still have only one marker
    final var markers = store.rangeMarkers(partitionId).join();
    assertThat(markers).containsExactly(marker);
  }

  @Test
  default void shouldDeleteMarker() {
    // given
    final var store = getStore();
    final int partitionId = 1;
    final var marker = new Start(100L);
    store.storeRangeMarker(partitionId, marker).join();

    // when
    assertThat(store.deleteRangeMarker(partitionId, marker)).succeedsWithin(Duration.ofSeconds(10));

    // then
    final var markers = store.rangeMarkers(partitionId).join();
    assertThat(markers).isEmpty();
  }

  @Test
  default void shouldDeleteNonExistentMarkerSilently() {
    // given
    final var store = getStore();
    final int partitionId = 1;
    final var marker = new Start(100L);

    // when / then - should not throw
    assertThat(store.deleteRangeMarker(partitionId, marker)).succeedsWithin(Duration.ofSeconds(10));
  }

  @Test
  default void shouldDeleteOnlySpecifiedMarker() {
    // given
    final var store = getStore();
    final int partitionId = 1;
    final var markerToDelete = new Start(100L);
    final var markerToKeep = new End(200L);
    store.storeRangeMarker(partitionId, markerToDelete).join();
    store.storeRangeMarker(partitionId, markerToKeep).join();

    // when
    store.deleteRangeMarker(partitionId, markerToDelete).join();

    // then
    final var markers = store.rangeMarkers(partitionId).join();
    assertThat(markers).containsExactly(markerToKeep);
  }

  @Test
  default void shouldDeleteMarkerFromCorrectPartitionOnly() {
    // given
    final var store = getStore();
    final int partition1 = 1;
    final int partition2 = 2;
    final var marker = new Start(100L);
    store.storeRangeMarker(partition1, marker).join();
    store.storeRangeMarker(partition2, marker).join();

    // when
    store.deleteRangeMarker(partition1, marker).join();

    // then
    assertThat(store.rangeMarkers(partition1).join()).isEmpty();
    assertThat(store.rangeMarkers(partition2).join()).containsExactly(marker);
  }
}
