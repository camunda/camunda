/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import static io.camunda.zeebe.backup.api.Util.descriptor;
import static io.camunda.zeebe.backup.api.Util.id;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.api.RangeIndex.Range;
import org.junit.jupiter.api.Test;

final class RangeIndexTest {

  @Test
  void shouldAddNewRange() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup3 = id(3);

    // when
    rangeIndex.add(backup1, descriptor(null, null));
    rangeIndex.add(backup3, descriptor(null, null));

    // then
    assertThat(rangeIndex.descendingRanges())
        .containsExactly(new Range(backup3, backup3), new Range(backup1, backup1));
  }

  @Test
  void shouldAddToRange() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup2 = id(2);

    // when
    rangeIndex.add(backup1, descriptor(null, null));
    rangeIndex.add(backup2, descriptor(backup1, null));

    // then
    assertThat(rangeIndex.descendingRanges())
        .singleElement()
        .isEqualTo(new Range(backup1, backup2));
  }

  @Test
  void shouldAddDuplicates() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);

    // when
    rangeIndex.add(backup1, descriptor(null, null));
    rangeIndex.add(backup1, descriptor(null, null));

    // then
    assertThat(rangeIndex.descendingRanges())
        .singleElement()
        .isEqualTo(new Range(backup1, backup1));
  }

  @Test
  void shouldAddContainedBackups() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup2 = id(2);
    final var backup3 = id(3);
    rangeIndex.add(backup1, descriptor(null, null));
    rangeIndex.add(backup2, descriptor(backup1, null));
    rangeIndex.add(backup3, descriptor(backup2, null));

    // when
    rangeIndex.add(backup2, descriptor(backup1, backup3));

    // then
    assertThat(rangeIndex.descendingRanges())
        .singleElement()
        .isEqualTo(new Range(backup1, backup3));
  }

  @Test
  void shouldMergeAdjacentRanges() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup2 = id(2);
    final var backup3 = id(3);

    // when
    rangeIndex.add(backup1, descriptor(null, null));
    rangeIndex.add(backup3, descriptor(null, null));
    rangeIndex.add(backup2, descriptor(backup1, backup3));

    // then
    assertThat(rangeIndex.descendingRanges())
        .singleElement()
        .isEqualTo(new Range(backup1, backup3));
  }

  @Test
  void shouldMergeGaps() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup5 = id(5);
    final var backup10 = id(10);

    rangeIndex.add(backup1, descriptor(null, null));
    rangeIndex.add(backup10, descriptor(null, null));

    // when - adding a backup that fills the gap
    rangeIndex.add(backup5, descriptor(backup1, backup10));

    // then - all ranges are merged
    assertThat(rangeIndex.descendingRanges())
        .singleElement()
        .isEqualTo(new Range(backup1, backup10));
  }

  @Test
  void shouldRemoveSingleBackupRange() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup3 = id(3);
    rangeIndex.add(backup1, descriptor(null, null));
    rangeIndex.add(backup3, descriptor(null, null));

    // when
    rangeIndex.remove(backup3, descriptor(null, null));

    // then
    assertThat(rangeIndex.descendingRanges())
        .singleElement()
        .isEqualTo(new Range(backup1, backup1));
  }

  @Test
  void shouldRemoveFromContainingRange() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup2 = id(2);
    final var backup3 = id(3);
    rangeIndex.add(backup1, descriptor(null, null));
    rangeIndex.add(backup2, descriptor(backup1, null));
    rangeIndex.add(backup3, descriptor(backup2, null));

    // when
    rangeIndex.remove(backup2, descriptor(backup1, backup3));

    // then
    assertThat(rangeIndex.descendingRanges())
        .containsExactly(new Range(backup3, backup3), new Range(backup1, backup1));
  }

  @Test
  void shouldRemoveFromRangeLeftBoundary() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup2 = id(2);
    final var backup3 = id(3);
    rangeIndex.add(backup1, descriptor(null, null));
    rangeIndex.add(backup2, descriptor(backup1, null));
    rangeIndex.add(backup3, descriptor(backup2, null));

    // when
    rangeIndex.remove(backup1, descriptor(null, backup2));

    // then
    assertThat(rangeIndex.descendingRanges()).containsExactly(new Range(backup2, backup3));
  }

  @Test
  void shouldRemoveFromRangeRightBoundary() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup2 = id(2);
    final var backup3 = id(3);
    rangeIndex.add(backup1, descriptor(null, null));
    rangeIndex.add(backup2, descriptor(backup1, null));
    rangeIndex.add(backup3, descriptor(backup2, null));

    // when
    rangeIndex.remove(backup3, descriptor(backup2, null));

    // then
    assertThat(rangeIndex.descendingRanges()).containsExactly(new Range(backup1, backup2));
  }

  @Test
  void shouldRemoveFromMiddleRangeAtBoundary() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup2 = id(2);
    final var backup4 = id(4);
    final var backup5 = id(5);
    rangeIndex.add(backup1, descriptor(null, null));
    rangeIndex.add(backup2, descriptor(backup1, null));

    rangeIndex.add(backup4, descriptor(null, null));
    rangeIndex.add(backup5, descriptor(backup4, null));

    // when
    rangeIndex.remove(backup2, descriptor(backup1, null));

    // then
    assertThat(rangeIndex.descendingRanges())
        .containsExactly(new Range(backup4, backup5), new Range(backup1, backup1));
  }

  @Test
  void shouldRemoveNonExistingBackup() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup2 = id(2);
    final var backup3 = id(3);
    rangeIndex.add(backup1, descriptor(null, null));
    rangeIndex.add(backup3, descriptor(null, null));

    // when
    rangeIndex.remove(backup2, descriptor(null, null));

    // then
    assertThat(rangeIndex.descendingRanges())
        .containsExactly(new Range(backup3, backup3), new Range(backup1, backup1));
  }

  @Test
  void shouldHandleMultipleNonAdjacentRanges() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup2 = id(2);
    final var backup5 = id(5);
    final var backup6 = id(6);
    final var backup10 = id(10);

    // when - add multiple non-adjacent ranges
    rangeIndex.add(backup1, descriptor(null, backup2));
    rangeIndex.add(backup5, descriptor(null, backup6));
    rangeIndex.add(backup10, descriptor(null, null));

    // then
    assertThat(rangeIndex.descendingRanges())
        .containsExactly(
            new Range(backup10, backup10),
            new Range(backup5, backup6),
            new Range(backup1, backup2));
  }

  @Test
  void shouldLookupWithGaps() {
    // given
    final var rangeIndex = new RangeIndex();
    final var backup1 = id(1);
    final var backup2 = id(2);
    final var backup5 = id(5);
    final var backup6 = id(6);

    rangeIndex.add(backup1, descriptor(null, backup2));
    rangeIndex.add(backup5, descriptor(null, backup6));

    // when/then - lookups in the gap should return null
    assertThat(rangeIndex.lookup(id(3))).isNull();
    assertThat(rangeIndex.lookup(id(4))).isNull();
    // lookups at range boundaries should succeed
    assertThat(rangeIndex.lookup(backup1)).isNotNull();
    assertThat(rangeIndex.lookup(backup2)).isNotNull();
    assertThat(rangeIndex.lookup(backup5)).isNotNull();
    assertThat(rangeIndex.lookup(backup6)).isNotNull();
  }
}
