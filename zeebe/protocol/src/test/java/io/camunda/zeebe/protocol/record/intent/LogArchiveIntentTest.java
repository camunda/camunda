/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.protocol.record.intent;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import org.junit.jupiter.api.Test;

final class LogArchiveIntentTest {

  @Test
  void shouldDecodeMergeAndReleaseIntentsByValue() {
    // given
    final short mergeLog = 12;
    final short logMerged = 13;
    final short releaseLog = 14;
    final short logReleased = 15;

    // when / then
    assertThat(LogArchiveIntent.from(mergeLog)).isEqualTo(LogArchiveIntent.MERGE_LOG);
    assertThat(LogArchiveIntent.from(logMerged)).isEqualTo(LogArchiveIntent.LOG_MERGED);
    assertThat(LogArchiveIntent.from(releaseLog)).isEqualTo(LogArchiveIntent.RELEASE_LOG);
    assertThat(LogArchiveIntent.from(logReleased)).isEqualTo(LogArchiveIntent.LOG_RELEASED);
  }

  @Test
  void shouldTreatOnlyLogMergedAndLogReleasedAsEvents() {
    // when / then
    assertThat(LogArchiveIntent.MERGE_LOG.isEvent()).isFalse();
    assertThat(LogArchiveIntent.LOG_MERGED.isEvent()).isTrue();
    assertThat(LogArchiveIntent.RELEASE_LOG.isEvent()).isFalse();
    assertThat(LogArchiveIntent.LOG_RELEASED.isEvent()).isTrue();
  }

  @Test
  void shouldDecodeHoldIntentsByValue() {
    // given
    final short moveHold = 16;
    final short holdMoved = 17;

    // when / then
    assertThat(LogArchiveIntent.from(moveHold)).isEqualTo(LogArchiveIntent.MOVE_HOLD);
    assertThat(LogArchiveIntent.from(holdMoved)).isEqualTo(LogArchiveIntent.HOLD_MOVED);
  }

  @Test
  void shouldTreatOnlyHoldMovedAsEvent() {
    // when / then
    assertThat(LogArchiveIntent.MOVE_HOLD.isEvent()).isFalse();
    assertThat(LogArchiveIntent.HOLD_MOVED.isEvent()).isTrue();
  }
}
