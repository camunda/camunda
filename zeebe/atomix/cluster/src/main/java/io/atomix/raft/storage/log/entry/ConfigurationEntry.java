/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.storage.log.entry;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.storage.serializer.RaftEntrySerializer;
import io.atomix.raft.storage.serializer.RaftEntrySerializer.SerializedBufferWriterAdapter;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collection;
import java.util.Collections;

/**
 * Stores a cluster configuration.
 *
 * <p>The {@code ConfigurationEntry} stores information relevant to a single cluster configuration
 * change. Configuration change entries store a collection of {@link RaftMember newMembers} which
 * each represent a server in the cluster. Each time the set of members changes or a property of a
 * single member changes, a new {@code ConfigurationEntry} must be logged for the configuration
 * change. If the set of members changes, the {@code ConfigurationEntry} must be committed by a
 * quorum of the old members and a quorum of the new members.
 */
public record ConfigurationEntry(
    long timestamp,
    Collection<RaftMember> newMembers,
    Collection<RaftMember> oldMembers,
    long compactionBound)
    implements RaftEntry {

  public ConfigurationEntry {
    checkNotNull(newMembers, "newMembers cannot be null");
    checkNotNull(oldMembers, "oldMembers cannot be null");
  }

  public ConfigurationEntry(final long timestamp, final Collection<RaftMember> newMembers) {
    this(timestamp, newMembers, Collections.emptyList(), -1);
  }

  /**
   * @return true if the configuration change requires joint consensus, i.e. when the set of members
   *     has changed.
   */
  public boolean requiresJointConsensus() {
    return !oldMembers.isEmpty();
  }

  @Override
  public BufferWriter toSerializable(final long term, final RaftEntrySerializer serializer) {
    // TODO: Update serialization to include compactionBound
    return new SerializedBufferWriterAdapter(
        () -> serializer.getConfigurationEntrySerializedLength(this),
        (buffer, offset) -> serializer.writeConfigurationEntry(term, this, buffer, offset));
  }
}
