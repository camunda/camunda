/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

final class LogArchiveKeysTest {

  @Test
  void shouldZeroPadNumbersTo20Digits() {
    // given
    final long seq = 4127;

    // when
    final String key = LogArchiveKeys.commitKey(seq);

    // then
    assertThat(key).isEqualTo("commits/00000000000000004127.json");
  }

  @Test
  void shouldBuildPartitionPrefix() {
    // given
    final String timelineId = "tl-0";
    final int partitionId = 1;

    // when
    final String prefix = LogArchiveKeys.partitionPrefix(timelineId, partitionId);

    // then
    assertThat(prefix).isEqualTo("log-archive/tl-0/p1/");
  }

  @Test
  void shouldBuildDataKeyAsInSpec() {
    // given
    final long firstIndex = 2731001;
    final long lastIndex = 2731480;
    final long term = 7;

    // when
    final String key = LogArchiveKeys.dataKey(firstIndex, lastIndex, term);

    // then
    assertThat(key).isEqualTo("data/00000000000002731001-00000000000002731480-t7.rec");
  }

  @Test
  void shouldBuildIndexRootSnapshotAndLatestKeys() {
    // given
    final String snapshotId = "snap-2731000";

    // when / then
    assertThat(LogArchiveKeys.indexKey(2600001, 2731000))
        .isEqualTo("index/00000000000002600001-00000000000002731000.json");
    assertThat(LogArchiveKeys.rootKey(4100)).isEqualTo("root/00000000000000004100.json");
    assertThat(LogArchiveKeys.latestKey()).isEqualTo("_latest.json");
    assertThat(LogArchiveKeys.snapshotFileKey(snapshotId, "000123.sst"))
        .isEqualTo("state/snap-2731000/000123.sst");
    assertThat(LogArchiveKeys.snapshotDescriptorKey(snapshotId))
        .isEqualTo("state/snap-2731000/descriptor.json");
  }

  @Test
  void shouldParseSeqFromCommitKeyWhenRoundTripping() {
    // given
    final List<Long> seqs = List.of(1L, 42L, 4127L, Long.MAX_VALUE);

    for (final long seq : seqs) {
      // when
      final long parsed = LogArchiveKeys.seqFromCommitKey(LogArchiveKeys.commitKey(seq));

      // then
      assertThat(parsed).isEqualTo(seq);
    }
  }

  @Test
  void shouldParseSeqFromRootKeyWithPartitionPrefix() {
    // given
    final String key = LogArchiveKeys.partitionPrefix("tl-0", 1) + LogArchiveKeys.rootKey(4100);

    // when
    final long seq = LogArchiveKeys.seqFromRootKey(key);

    // then
    assertThat(seq).isEqualTo(4100);
  }

  @Test
  void shouldRejectKeysOfTheWrongKindWhenParsingSeq() {
    // given
    final String rootKey = LogArchiveKeys.rootKey(1);
    final String unpadded = "commits/4.json";
    final String notAKey = "xcommits/00000000000000000001.json";

    // when / then
    assertThatThrownBy(() -> LogArchiveKeys.seqFromCommitKey(rootKey))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LogArchiveKeys.seqFromCommitKey(unpadded))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LogArchiveKeys.seqFromCommitKey(notAKey))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LogArchiveKeys.seqFromRootKey(LogArchiveKeys.commitKey(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNegativeNumbers() {
    // given / when / then
    assertThatThrownBy(() -> LogArchiveKeys.commitKey(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LogArchiveKeys.dataKey(-1, 5, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldOrderCommitKeysLexicographicallyAsNumbers() {
    // given
    final List<Long> seqs = List.of(1L, 9L, 10L, 100L, 4127L, 999_999_999_999L, Long.MAX_VALUE);
    final List<String> keysInNumericOrder = seqs.stream().map(LogArchiveKeys::commitKey).toList();

    // when
    final List<String> sortedKeys = keysInNumericOrder.stream().sorted().toList();

    // then
    assertThat(sortedKeys).containsExactlyElementsOf(keysInNumericOrder);
    assertThat(sortedKeys.stream().map(LogArchiveKeys::seqFromCommitKey).toList())
        .containsExactlyElementsOf(seqs);
  }
}
