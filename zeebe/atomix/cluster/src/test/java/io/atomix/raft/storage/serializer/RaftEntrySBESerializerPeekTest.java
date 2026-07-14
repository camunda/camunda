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
package io.atomix.raft.storage.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.raft.storage.log.entry.InitialEntry;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Pins the allocation-free header peek ({@link RaftEntrySBESerializer#isApplicationEntry} plus the
 * data-bounds accessors) against the full deserialization path ({@link
 * RaftEntrySBESerializer#readRaftLogEntry}) on the same buffers. The peek serves hot read paths
 * (index scans, per-block append hooks), so any divergence from the full path corrupts served data.
 */
final class RaftEntrySBESerializerPeekTest {

  private final RaftEntrySBESerializer serializer = new RaftEntrySBESerializer();
  private final MutableDirectBuffer buffer = new ExpandableArrayBuffer();

  @Test
  void shouldPeekSameApplicationDataBoundsAsFullDeserialization() {
    // given
    final byte[] data = "application-data".getBytes(StandardCharsets.UTF_8);
    final var entry = new SerializedApplicationEntry(1, 2, new UnsafeBuffer(data));
    serializer.writeApplicationEntry(5, entry, buffer, 0);

    // when
    final boolean isApplicationEntry = serializer.isApplicationEntry(buffer);

    // then
    assertThat(isApplicationEntry).isTrue();
    assertThat(serializer.applicationDataLength()).isEqualTo(data.length);
    assertThat(serializer.applicationDataOffset())
        .isEqualTo(serializer.getApplicationEntrySerializedHeaderLength());
    assertThat(peekedData(buffer)).isEqualTo(fullyDeserializedData(buffer)).isEqualTo(data);
  }

  @Test
  void shouldPeekApplicationEntryWrittenAtNonZeroOffset() {
    // given - the entry serialized at an arbitrary offset, exposed as a slice like a journal
    // record's data buffer
    final int offset = 16;
    final byte[] data = "sliced-application-data".getBytes(StandardCharsets.UTF_8);
    final var entry = new SerializedApplicationEntry(3, 7, new UnsafeBuffer(data));
    final int length = serializer.writeApplicationEntry(5, entry, buffer, offset);
    final DirectBuffer slice = new UnsafeBuffer(buffer, offset, length);

    // when
    final boolean isApplicationEntry = serializer.isApplicationEntry(slice);

    // then
    assertThat(isApplicationEntry).isTrue();
    assertThat(peekedData(slice)).isEqualTo(fullyDeserializedData(slice)).isEqualTo(data);
  }

  @Test
  void shouldPeekZeroLengthApplicationData() {
    // given
    final var entry = new SerializedApplicationEntry(1, 1, new UnsafeBuffer(new byte[0]));
    serializer.writeApplicationEntry(5, entry, buffer, 0);

    // when
    final boolean isApplicationEntry = serializer.isApplicationEntry(buffer);

    // then
    assertThat(isApplicationEntry).isTrue();
    assertThat(serializer.applicationDataLength()).isZero();
    assertThat(peekedData(buffer)).isEqualTo(fullyDeserializedData(buffer)).isEmpty();
  }

  @Test
  void shouldNotIdentifyInitialEntryAsApplicationEntry() {
    // given
    serializer.writeInitialEntry(5, new InitialEntry(), buffer, 0);

    // when
    final boolean isApplicationEntry = serializer.isApplicationEntry(buffer);

    // then - the peek agrees with the full deserialization
    assertThat(isApplicationEntry).isFalse();
    assertThat(serializer.readRaftLogEntry(buffer).isApplicationEntry()).isFalse();
  }

  @Test
  void shouldNotIdentifyConfigurationEntryAsApplicationEntry() {
    // given
    final Set<RaftMember> members =
        Set.of(
            new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.ofEpochMilli(123456L)),
            new DefaultRaftMember(MemberId.from("2"), Type.PASSIVE, Instant.ofEpochMilli(123457L)));
    serializer.writeConfigurationEntry(5, new ConfigurationEntry(1234L, members), buffer, 0);

    // when
    final boolean isApplicationEntry = serializer.isApplicationEntry(buffer);

    // then - the peek agrees with the full deserialization
    assertThat(isApplicationEntry).isFalse();
    assertThat(serializer.readRaftLogEntry(buffer).isApplicationEntry()).isFalse();
  }

  @Test
  void shouldPeekCorrectlyAfterDecodingADifferentEntry() {
    // given - the serializer's reused decoders were last wrapped over another entry
    final var otherBuffer = new ExpandableArrayBuffer();
    serializer.writeInitialEntry(3, new InitialEntry(), otherBuffer, 0);
    serializer.readRaftLogEntry(otherBuffer);

    final byte[] data = "interleaved".getBytes(StandardCharsets.UTF_8);
    final var entry = new SerializedApplicationEntry(8, 9, new UnsafeBuffer(data));
    serializer.writeApplicationEntry(5, entry, buffer, 0);

    // when
    final boolean isApplicationEntry = serializer.isApplicationEntry(buffer);

    // then
    assertThat(isApplicationEntry).isTrue();
    assertThat(peekedData(buffer)).isEqualTo(fullyDeserializedData(buffer)).isEqualTo(data);
  }

  /** Reads the application data through the peek's offset/length accessors. */
  private byte[] peekedData(final DirectBuffer entryBuffer) {
    assertThat(serializer.isApplicationEntry(entryBuffer)).isTrue();
    final var bytes = new byte[serializer.applicationDataLength()];
    entryBuffer.getBytes(serializer.applicationDataOffset(), bytes);
    return bytes;
  }

  /** Reads the application data through the full {@code readRaftLogEntry} path. */
  private byte[] fullyDeserializedData(final DirectBuffer entryBuffer) {
    final var raftEntry = serializer.readRaftLogEntry(entryBuffer);
    assertThat(raftEntry.isApplicationEntry()).isTrue();
    final var applicationEntry = (SerializedApplicationEntry) raftEntry.getApplicationEntry();
    final var bytes = new byte[applicationEntry.data().capacity()];
    applicationEntry.data().getBytes(0, bytes);
    return bytes;
  }
}
