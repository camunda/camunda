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
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.raft.storage.log.entry.InitialEntry;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class RaftEntrySBESerializerTest {

  private static final UUID EPOCH = UUID.randomUUID();

  final RaftEntrySerializer serializer = new RaftEntrySBESerializer();
  final MutableDirectBuffer buffer = new ExpandableArrayBuffer();

  @Test
  public void shouldCalculateActualApplicationEntrySize() {
    // given
    final byte[] data = "Test".getBytes();
    final SerializedApplicationEntry applicationEntry =
        new SerializedApplicationEntry(1, 2, new UnsafeBuffer(data));

    // when
    final int writtenBytes =
        serializer.writeApplicationEntry(5, EPOCH, applicationEntry, buffer, 0);

    // then
    assertThat(serializer.getApplicationEntrySerializedLength(applicationEntry))
        .isEqualTo(writtenBytes);
  }

  @Test
  public void shouldWriteApplicationEntry() {
    // given
    final byte[] data = "Test".getBytes();
    final SerializedApplicationEntry applicationEntryWritten =
        new SerializedApplicationEntry(1, 2, new UnsafeBuffer(data));
    final RaftLogEntry raftLogEntryExpected = new RaftLogEntry(5, EPOCH, applicationEntryWritten);

    // when
    serializer.writeApplicationEntry(5, EPOCH, applicationEntryWritten, buffer, 0);
    final RaftLogEntry raftLogEntryRead = serializer.readRaftLogEntry(buffer);

    assertThat(raftLogEntryRead.isApplicationEntry()).isTrue();

    final ApplicationEntry applicationEntryRead = raftLogEntryRead.getApplicationEntry();

    // then
    assertThat(applicationEntryRead).isEqualTo(applicationEntryWritten);
    assertThat(raftLogEntryRead).isEqualTo(raftLogEntryExpected);
  }

  @Test
  public void shouldWriteApplicationEntryAtAnyOffset() {
    // given
    final int offset = 10;
    final byte[] data = "Test".getBytes();
    final SerializedApplicationEntry applicationEntryWritten =
        new SerializedApplicationEntry(1, 2, new UnsafeBuffer(data));
    final RaftLogEntry raftLogEntryExpected = new RaftLogEntry(5, EPOCH, applicationEntryWritten);

    // when
    final var length =
        serializer.writeApplicationEntry(5, EPOCH, applicationEntryWritten, buffer, offset);
    final RaftLogEntry raftLogEntryRead =
        serializer.readRaftLogEntry(new UnsafeBuffer(buffer, offset, length));

    assertThat(raftLogEntryRead.isApplicationEntry()).isTrue();

    final ApplicationEntry applicationEntryRead = raftLogEntryRead.getApplicationEntry();

    // then
    assertThat(applicationEntryRead).isEqualTo(applicationEntryWritten);
    assertThat(raftLogEntryRead).isEqualTo(raftLogEntryExpected);
  }

  @Test
  public void shouldCalculateActualInitialEntrySize() {
    // given
    final InitialEntry initialEntry = new InitialEntry();

    // when
    final int writtenBytes = serializer.writeInitialEntry(5, EPOCH, initialEntry, buffer, 0);

    // then
    assertThat(serializer.getInitialEntrySerializedLength()).isEqualTo(writtenBytes);
  }

  @Test
  public void shouldWriteInitialEntry() {
    // given
    final InitialEntry initialEntryWritten = new InitialEntry();
    final RaftLogEntry raftLogEntryExpected = new RaftLogEntry(5, EPOCH, initialEntryWritten);

    // when
    serializer.writeInitialEntry(5, EPOCH, initialEntryWritten, buffer, 0);
    final RaftLogEntry raftLogEntryRead = serializer.readRaftLogEntry(buffer);

    assertThat(raftLogEntryRead.isInitialEntry()).isTrue();
    assertThat(raftLogEntryRead).isEqualTo(raftLogEntryExpected);
  }

  @Test
  public void shouldWriteInitialEntryAtAnyOffset() {
    // given
    final int offset = 10;
    final InitialEntry initialEntryWritten = new InitialEntry();
    final RaftLogEntry raftLogEntryExpected = new RaftLogEntry(5, EPOCH, initialEntryWritten);

    // when
    final var length = serializer.writeInitialEntry(5, EPOCH, initialEntryWritten, buffer, offset);
    final RaftLogEntry raftLogEntryRead =
        serializer.readRaftLogEntry(new UnsafeBuffer(buffer, offset, length));

    assertThat(raftLogEntryRead.isInitialEntry()).isTrue();
    assertThat(raftLogEntryExpected).isEqualTo(raftLogEntryRead);
  }

  @Test
  public void shouldCalculateActualConfigurationEntrySize() {
    // given
    final Set<RaftMember> members =
        Set.of(
            new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.ofEpochMilli(123456L)),
            new DefaultRaftMember(
                MemberId.from("222"), Type.PASSIVE, Instant.ofEpochMilli(123457L)),
            new DefaultRaftMember(MemberId.from(""), Type.PASSIVE, Instant.ofEpochMilli(123457L)),
            new DefaultRaftMember(
                MemberId.from("hello1"), Type.PROMOTABLE, Instant.ofEpochMilli(123458L)));
    final ConfigurationEntry configurationEntry = new ConfigurationEntry(1234L, members);

    // when
    final int writtenBytes =
        serializer.writeConfigurationEntry(5, EPOCH, configurationEntry, buffer, 0);

    // then
    assertThat(serializer.getConfigurationEntrySerializedLength(configurationEntry))
        .isEqualTo(writtenBytes);
  }

  @Test
  public void shouldWriteConfigurationEntry() {
    // given
    final Set<RaftMember> members =
        Set.of(
            new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.ofEpochMilli(123456L)),
            new DefaultRaftMember(MemberId.from("2"), Type.PASSIVE, Instant.ofEpochMilli(123457L)),
            new DefaultRaftMember(
                MemberId.from("3"), Type.PROMOTABLE, Instant.ofEpochMilli(123458L)));
    final ConfigurationEntry configurationEntryWritten = new ConfigurationEntry(1234L, members);

    // when
    serializer.writeConfigurationEntry(5, EPOCH, configurationEntryWritten, buffer, 0);
    final RaftLogEntry entryRead = serializer.readRaftLogEntry(buffer);

    // then
    assertThat(entryRead.isConfigurationEntry()).isTrue();
    assertThat(entryRead.epoch()).isEqualTo(EPOCH);
    final var configurationEntryRead = entryRead.getConfigurationEntry();
    assertThat(configurationEntryRead.requiresJointConsensus()).isFalse();
    assertThat(configurationEntryRead.timestamp()).isEqualTo(configurationEntryWritten.timestamp());
    assertThat(configurationEntryRead.toString()).isEqualTo(configurationEntryWritten.toString());
  }

  @Test
  public void shouldWriteConfigurationEntryAtAnyOffset() {
    // given
    final int offset = 10;
    final Set<RaftMember> members =
        Set.of(
            new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.ofEpochMilli(123456L)),
            new DefaultRaftMember(MemberId.from("2"), Type.PASSIVE, Instant.ofEpochMilli(123457L)),
            new DefaultRaftMember(
                MemberId.from("3"), Type.PROMOTABLE, Instant.ofEpochMilli(123458L)));
    final ConfigurationEntry configurationEntryWritten = new ConfigurationEntry(1234L, members);

    // when
    final var length =
        serializer.writeConfigurationEntry(5, EPOCH, configurationEntryWritten, buffer, offset);
    final RaftLogEntry entryRead =
        serializer.readRaftLogEntry(new UnsafeBuffer(buffer, offset, length));

    // then
    assertThat(entryRead.isConfigurationEntry()).isTrue();
    final var configurationEntryRead = entryRead.getConfigurationEntry();
    assertThat(configurationEntryRead.timestamp()).isEqualTo(configurationEntryWritten.timestamp());
    assertThat(configurationEntryRead.toString()).isEqualTo(configurationEntryWritten.toString());
  }

  @Test
  public void shouldWriteConfigurationEntryWithChangedMembers() {
    // given
    final Set<RaftMember> oldMembers =
        Set.of(
            new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.ofEpochMilli(123456L)),
            new DefaultRaftMember(MemberId.from("2"), Type.ACTIVE, Instant.ofEpochMilli(123457L)),
            new DefaultRaftMember(MemberId.from("3"), Type.ACTIVE, Instant.ofEpochMilli(123458L)));
    final Set<RaftMember> newMembers =
        Set.of(
            new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.ofEpochMilli(123456L)),
            new DefaultRaftMember(MemberId.from("2"), Type.ACTIVE, Instant.ofEpochMilli(123457L)),
            new DefaultRaftMember(MemberId.from("3"), Type.ACTIVE, Instant.ofEpochMilli(123458L)),
            new DefaultRaftMember(MemberId.from("4"), Type.ACTIVE, Instant.ofEpochMilli(123459L)));

    final ConfigurationEntry configurationEntryWritten =
        new ConfigurationEntry(1234L, oldMembers, newMembers);

    // when
    serializer.writeConfigurationEntry(5, EPOCH, configurationEntryWritten, buffer, 0);
    final RaftLogEntry entryRead = serializer.readRaftLogEntry(buffer);

    // then
    assertThat(entryRead.isConfigurationEntry()).isTrue();
    final var configurationEntryRead = entryRead.getConfigurationEntry();
    assertThat(configurationEntryRead.requiresJointConsensus()).isTrue();
    assertThat(configurationEntryRead.timestamp()).isEqualTo(configurationEntryWritten.timestamp());
    assertThat(configurationEntryRead.toString()).isEqualTo(configurationEntryWritten.toString());
  }

  @Test
  public void shouldCalculateActualConfigurationEntrySizeWithOldAndNewMembers() {
    // given
    final Set<RaftMember> oldMembers =
        Set.of(
            new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.ofEpochMilli(123456L)),
            new DefaultRaftMember(MemberId.from("2"), Type.PASSIVE, Instant.ofEpochMilli(123457L)),
            new DefaultRaftMember(MemberId.from("3"), Type.PASSIVE, Instant.ofEpochMilli(123457L)));

    final Set<RaftMember> newMembers =
        Set.of(
            new DefaultRaftMember(MemberId.from("2"), Type.ACTIVE, Instant.ofEpochMilli(123457L)),
            new DefaultRaftMember(MemberId.from("3"), Type.ACTIVE, Instant.ofEpochMilli(123457L)),
            new DefaultRaftMember(MemberId.from("4"), Type.ACTIVE, Instant.ofEpochMilli(123457L)),
            new DefaultRaftMember(MemberId.from("5"), Type.ACTIVE, Instant.ofEpochMilli(123457L)));

    final ConfigurationEntry configurationEntry =
        new ConfigurationEntry(1234L, newMembers, oldMembers);

    // when
    final int writtenBytes =
        serializer.writeConfigurationEntry(5, EPOCH, configurationEntry, buffer, 0);

    // then
    assertThat(serializer.getConfigurationEntrySerializedLength(configurationEntry))
        .isEqualTo(writtenBytes);
  }

  @Test
  public void shouldWriteEpochInCanonicalByteOrder() {
    // given
    final UUID epoch = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

    // when
    serializer.writeInitialEntry(5, epoch, new InitialEntry(), buffer, 0);

    // then
    final byte[] written = new byte[RaftLogEntryEncoder.epochLength()];
    buffer.getBytes(
        MessageHeaderEncoder.ENCODED_LENGTH + RaftLogEntryEncoder.epochEncodingOffset(), written);
    assertThat(HexFormat.of().formatHex(written)).isEqualTo("00112233445566778899aabbccddeeff");
  }

  @Test
  public void shouldMakeEpochFromTermForEntryWrittenBeforeEpochs() {
    // given an application entry as schema version 4 wrote it: its RaftLogEntry block ends where
    // the epoch starts now
    final SerializedApplicationEntry applicationEntry =
        new SerializedApplicationEntry(1, 2, new UnsafeBuffer("Test".getBytes()));
    final MutableDirectBuffer current = new ExpandableArrayBuffer();
    final int length = serializer.writeApplicationEntry(5, EPOCH, applicationEntry, current, 0);

    final int headerLength = MessageHeaderEncoder.ENCODED_LENGTH;
    final int oldBlockLength = RaftLogEntryEncoder.epochEncodingOffset();
    final int applicationEntryOffset = headerLength + RaftLogEntryEncoder.BLOCK_LENGTH;
    final int oldLength = length - (RaftLogEntryEncoder.BLOCK_LENGTH - oldBlockLength);
    buffer.putBytes(0, current, 0, headerLength + oldBlockLength);
    buffer.putBytes(
        headerLength + oldBlockLength,
        current,
        applicationEntryOffset,
        length - applicationEntryOffset);
    new MessageHeaderEncoder().wrap(buffer, 0).blockLength(oldBlockLength).version(4);
    new MessageHeaderEncoder().wrap(buffer, headerLength + oldBlockLength).version(4);

    // when
    final RaftLogEntry entryRead =
        serializer.readRaftLogEntry(new UnsafeBuffer(buffer, 0, oldLength));

    // then
    assertThat(entryRead).isEqualTo(new RaftLogEntry(5, new UUID(0, 5), applicationEntry));
  }
}
