/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class ChangelogMarkerTest {

  @Test
  void shouldRoundTripTheSourceOffsetThroughTheMarkerValue() {
    // given
    final long sourceOffset = 4_242L;

    // when
    final byte[] value = ChangelogMarker.encodeValue(sourceOffset);

    // then
    assertThat(ChangelogMarker.decodeSourceOffset(value)).isEqualTo(sourceOffset);
  }

  @Test
  void shouldNeverCollideWithAnyRealGroupKey() {
    // given — every real changelog key is a store row key, at least 4 bytes: a GroupedCellStore
    // meta row is the bare big-endian group, and groups are allocated monotonically from 0
    for (int group = 0; group < 10_000; group++) {
      final byte[] metaKey = new byte[Integer.BYTES];
      metaKey[0] = (byte) (group >>> 24);
      metaKey[1] = (byte) (group >>> 16);
      metaKey[2] = (byte) (group >>> 8);
      metaKey[3] = (byte) group;

      // then — the marker's reserved key (sentinel group -1) never equals any allocated group's
      // meta key
      assertThat(ChangelogMarker.KEY).isNotEqualTo(metaKey);
    }
  }

  @Test
  void shouldRecognizeItsOwnKeyAndNothingElse() {
    assertThat(ChangelogMarker.isMarkerKey(ChangelogMarker.KEY)).isTrue();
    assertThat(ChangelogMarker.isMarkerKey(new byte[] {0, 0, 0, 1})).isFalse();
  }

  @Test
  void shouldRejectAnUnsupportedMarkerValue() {
    assertThatThrownBy(
            () -> ChangelogMarker.decodeSourceOffset(new byte[] {2, 0, 0, 0, 0, 0, 0, 0, 1}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ChangelogMarker.decodeSourceOffset(new byte[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
