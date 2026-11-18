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
import static org.assertj.core.api.Assertions.tuple;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Schema version 5 adds the incarnation of the members, the fence list and the voter policy to the
 * configuration. A configuration that a broker wrote with version 4 must still be read, with
 * incarnation 0, no fence entry and no policy.
 *
 * <p>The bytes were written with the encoders that SBE generates from the schema of origin/main
 * (version 4), with the same steps as its RaftEntrySBESerializer#writeConfigurationEntry and
 * MetaStoreSerializer#writeConfiguration: new members a_1 (ACTIVE, updated 11) and b_2 (PASSIVE,
 * updated 12), old member a_1 (ACTIVE, updated 10), timestamp 1234.
 */
final class ConfigurationVersion4CompatibilityTest {

  /** A log entry of term 5 with a ConfigurationEntry. */
  private static final String LOG_ENTRY_V4 =
      "09000100080004000500000000000000010800040008000400d204000000000000090002030b00000000000000"
          + "03000000615f31010c0000000000000003000000625f32090001030a0000000000000003000000615f31";

  /** The stored configuration of the meta store, with index 7 and term 3. */
  private static final String META_STORE_CONFIGURATION_V4 =
      "01190005000800040007000000000000000300000000000000d20400000000000000090002030b000000000000"
          + "0003000000615f31010c0000000000000003000000625f32090001030a0000000000000003000000615f31";

  @Test
  void shouldReadConfigurationEntryOfVersion4() {
    // given
    final var bytes = HexFormat.of().parseHex(LOG_ENTRY_V4);

    // when
    final var entry = new RaftEntrySBESerializer().readRaftLogEntry(new UnsafeBuffer(bytes));

    // then
    assertThat(entry.term()).isEqualTo(5);
    final var configuration = entry.getConfigurationEntry();
    assertThat(configuration.timestamp()).isEqualTo(1234);
    assertThat(configuration.newMembers())
        .extracting(RaftMember::memberId, RaftMember::getType, RaftMember::getIncarnation)
        .containsExactly(
            tuple(MemberId.from("a_1"), Type.ACTIVE, 0L),
            tuple(MemberId.from("b_2"), Type.PASSIVE, 0L));
    assertThat(configuration.oldMembers())
        .extracting(RaftMember::memberId, RaftMember::getType, RaftMember::getIncarnation)
        .containsExactly(tuple(MemberId.from("a_1"), Type.ACTIVE, 0L));
    assertThat(configuration.fenced()).isEmpty();
    assertThat(configuration.membershipVersion()).isZero();
  }

  @Test
  void shouldReadMetaStoreConfigurationOfVersion4() {
    // given
    final var bytes = HexFormat.of().parseHex(META_STORE_CONFIGURATION_V4);

    // when
    final var configuration = new MetaStoreSerializer().readConfiguration(ByteBuffer.wrap(bytes));

    // then
    assertThat(configuration.index()).isEqualTo(7);
    assertThat(configuration.term()).isEqualTo(3);
    assertThat(configuration.time()).isEqualTo(1234);
    assertThat(configuration.force()).isFalse();
    assertThat(configuration.newMembers())
        .extracting(RaftMember::memberId, RaftMember::getType, RaftMember::getIncarnation)
        .containsExactly(
            tuple(MemberId.from("a_1"), Type.ACTIVE, 0L),
            tuple(MemberId.from("b_2"), Type.PASSIVE, 0L));
    assertThat(configuration.oldMembers())
        .extracting(RaftMember::memberId, RaftMember::getType, RaftMember::getIncarnation)
        .containsExactly(tuple(MemberId.from("a_1"), Type.ACTIVE, 0L));
    assertThat(configuration.fenced()).isEmpty();
    assertThat(configuration.membershipVersion()).isZero();
  }
}
