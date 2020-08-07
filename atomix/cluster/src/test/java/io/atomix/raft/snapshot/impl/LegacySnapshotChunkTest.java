/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.snapshot.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.protocol.InstallRequest;
import java.nio.ByteBuffer;
import org.junit.Test;

public class LegacySnapshotChunkTest {
  @Test
  public void shouldHaveCorrectChunkName() {
    // given
    final var request =
        newInstallRequest(1, 1, System.currentTimeMillis(), "foo", "baz".getBytes());

    // when
    final var chunk = LegacySnapshotChunk.ofInstallRequest(request);

    // then
    assertThat(chunk.getChunkName()).isEqualTo("foo");
  }

  @Test
  public void shouldHaveCorrectSnapshotId() {
    // given
    final var request = newInstallRequest(2, 3, 10, "baz", "baz".getBytes());

    // when
    final var chunk = LegacySnapshotChunk.ofInstallRequest(request);

    // then
    assertThat(chunk.getSnapshotId()).isEqualTo("2-3-10");
  }

  @Test
  public void shouldHaveNullTotalCount() {
    // given
    final var request = newInstallRequest(2, 3, 10, "baz", "baz".getBytes());

    // when
    final var chunk = LegacySnapshotChunk.ofInstallRequest(request);

    // then
    assertThat(chunk.getTotalCount()).isEqualTo(FileBasedReceivedSnapshot.TOTAL_COUNT_NULL_VALUE);
  }

  @Test
  public void shouldHaveNullSnapshotChecksum() {
    // given
    final var request = newInstallRequest(2, 3, 10, "foo", "baz".getBytes());

    // when
    final var chunk = LegacySnapshotChunk.ofInstallRequest(request);

    // then
    assertThat(chunk.getSnapshotChecksum())
        .isEqualTo(FileBasedReceivedSnapshot.SNAPSHOT_CHECKSUM_NULL_VALUE);
  }

  @Test
  public void shouldHaveCorrectChecksum() {
    // given
    final byte[] content = "baz".getBytes();
    final long checksum = SnapshotChunkUtil.createChecksum(content);
    final var request = newInstallRequest(2, 3, 10, "foo", content);

    // when
    final var chunk = LegacySnapshotChunk.ofInstallRequest(request);

    // then
    assertThat(chunk.getChecksum()).isEqualTo(checksum);
  }

  @Test
  public void shouldHaveCorrectContent() {
    // given
    final byte[] content = "baz".getBytes();
    final var request = newInstallRequest(2, 3, 10, "foo", content);

    // when
    final var chunk = LegacySnapshotChunk.ofInstallRequest(request);

    // then
    assertThat(chunk.getContent()).isEqualTo(content);
  }

  private InstallRequest newInstallRequest(
      final long index,
      final int term,
      final long timestamp,
      final String chunkName,
      final byte[] content) {
    return new InstallRequest(
        1,
        MemberId.anonymous(),
        index,
        term,
        timestamp,
        1,
        ByteBuffer.wrap(chunkName.getBytes()),
        null,
        ByteBuffer.wrap(content),
        true,
        true);
  }
}
