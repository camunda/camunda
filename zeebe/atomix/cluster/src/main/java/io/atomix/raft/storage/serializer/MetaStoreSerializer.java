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

import static io.atomix.raft.storage.serializer.SerializerUtil.getRaftMemberType;
import static io.atomix.raft.storage.serializer.SerializerUtil.getSBEType;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.storage.system.Configuration;
import io.atomix.raft.storage.system.MetaStoreRecord;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MetaStoreSerializer {

  private static final byte VERSION = 1;
  private static final int VERSION_LENGTH = Byte.BYTES;
  private final ByteBuffer metaByteBuffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
  private final UnsafeBuffer metaBuffer = new UnsafeBuffer(metaByteBuffer);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final ConfigurationEncoder configurationEncoder = new ConfigurationEncoder();
  private final MetaEncoder metaEncoder = new MetaEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final ConfigurationDecoder configurationDecoder = new ConfigurationDecoder();
  private final MetaDecoder metaDecoder = new MetaDecoder();

  public MetaStoreSerializer() {
    metaBuffer.putByte(0, VERSION);
  }

  public ByteBuffer metaByteBuffer() {
    return metaByteBuffer.position(0);
  }

  public final ByteBuffer writeConfiguration(final Configuration configuration) {
    final var buffer = new ExpandableArrayBuffer(256);
    buffer.putByte(0, VERSION);
    configurationEncoder.wrapAndApplyHeader(buffer, VERSION_LENGTH, headerEncoder);

    configurationEncoder
        .index(configuration.index())
        .term(configuration.term())
        .timestamp(configuration.time())
        .force(configuration.force() ? BooleanType.TRUE : BooleanType.FALSE)
        .compactionBound(configuration.compactionBound());

    final var newMembersEncoder =
        configurationEncoder.newMembersCount(configuration.newMembers().size());
    for (final RaftMember member : configuration.newMembers()) {
      final var memberId = member.memberId().id();
      newMembersEncoder
          .next()
          .type(getSBEType(member.getType()))
          .updated(member.getLastUpdated().toEpochMilli())
          .memberId(memberId);
    }

    final var oldMembersEncoder =
        configurationEncoder.oldMembersCount(configuration.oldMembers().size());
    for (final RaftMember member : configuration.oldMembers()) {
      final var memberId = member.memberId().id();
      oldMembersEncoder
          .next()
          .type(getSBEType(member.getType()))
          .updated(member.getLastUpdated().toEpochMilli())
          .memberId(memberId);
    }

    final int totalLength =
        VERSION_LENGTH + headerEncoder.encodedLength() + configurationEncoder.encodedLength();
    final ByteBuffer bb = ByteBuffer.allocateDirect(totalLength);
    buffer.getBytes(0, bb, totalLength);
    return bb;
  }

  public Configuration readConfiguration(final ByteBuffer bb) {
    final var buffer = new UnsafeBuffer(bb);
    try {
      configurationDecoder.wrapAndApplyHeader(buffer, VERSION_LENGTH, headerDecoder);
    } catch (final IllegalStateException e) {
      return null;
    }

    final long index = configurationDecoder.index();
    final long term = configurationDecoder.term();
    final long timestamp = configurationDecoder.timestamp();
    final boolean force = BooleanType.TRUE.equals(configurationDecoder.force());
    final var compactionBound = configurationDecoder.compactionBound();

    final var newMembersDecoder = configurationDecoder.newMembers();
    final var newMembers = new ArrayList<RaftMember>(newMembersDecoder.count());
    for (final var member : newMembersDecoder) {
      final RaftMember.Type type = getRaftMemberType(member.type());
      final Instant updated = Instant.ofEpochMilli(member.updated());
      final var memberId = member.memberId();
      newMembers.add(new DefaultRaftMember(MemberId.from(memberId), type, updated));
    }

    final var oldMembersDecoder = configurationDecoder.oldMembers();
    final var oldMembers = new ArrayList<RaftMember>(oldMembersDecoder.count());
    for (final var member : oldMembersDecoder) {
      final RaftMember.Type type = getRaftMemberType(member.type());
      final Instant updated = Instant.ofEpochMilli(member.updated());
      final var memberId = member.memberId();
      oldMembers.add(new DefaultRaftMember(MemberId.from(memberId), type, updated));
    }

    return new Configuration(
        index, term, timestamp, newMembers, oldMembers, force, compactionBound);
  }

  public void writeTerm(final long term) {
    metaEncoder.wrapAndApplyHeader(metaBuffer, VERSION_LENGTH, headerEncoder);
    metaEncoder.term(term);
  }

  public long readTerm() {
    metaDecoder.wrapAndApplyHeader(metaBuffer, VERSION_LENGTH, headerDecoder);
    return metaDecoder.term();
  }

  public void writeVotedFor(final String memberId) {
    metaEncoder.wrapAndApplyHeader(metaBuffer, VERSION_LENGTH, headerEncoder);
    metaEncoder.votedFor(memberId);
  }

  public String readVotedFor() {
    metaDecoder.wrapAndApplyHeader(metaBuffer, VERSION_LENGTH, headerDecoder);
    return metaDecoder.votedFor();
  }

  public long readLastFlushedIndex() {
    metaDecoder.wrapAndApplyHeader(metaBuffer, VERSION_LENGTH, headerDecoder);
    return metaDecoder.lastFlushedIndex();
  }

  public void writeLastFlushedIndex(final long index) {
    metaEncoder.wrapAndApplyHeader(metaBuffer, VERSION_LENGTH, headerEncoder);
    metaEncoder.lastFlushedIndex(index);
  }

  public void writeCommitIndex(final long index) {
    metaEncoder.wrapAndApplyHeader(metaBuffer, VERSION_LENGTH, headerEncoder);
    metaEncoder.commitIndex(index);
  }

  public MetaStoreRecord readRecord() {
    metaDecoder.wrapAndApplyHeader(metaBuffer, VERSION_LENGTH, headerDecoder);
    final var term = metaDecoder.term();
    final var lastFlushedIndex = metaDecoder.lastFlushedIndex();
    final var commitIndex = commitIndexOrDefault(metaDecoder.commitIndex());
    final var votedFor = metaDecoder.votedForLength() == 0 ? null : metaDecoder.votedFor();
    return new MetaStoreRecord(term, lastFlushedIndex, commitIndex, votedFor);
  }

  public void writeRecord(final MetaStoreRecord record) {
    metaEncoder.wrapAndApplyHeader(metaBuffer, VERSION_LENGTH, headerEncoder);
    metaEncoder
        .term(record.term())
        .lastFlushedIndex(record.lastFlushedIndex())
        .commitIndex(record.commitIndex())
        .votedFor(record.votedFor());
  }

  private long commitIndexOrDefault(final long serializedCommitIndex) {
    return serializedCommitIndex == MetaDecoder.commitIndexNullValue() ? 0 : serializedCommitIndex;
  }
}
