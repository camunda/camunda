/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.raft.partition;

import com.esotericsoftware.kryo.serializers.FieldSerializer.Optional;
import io.atomix.primitive.partition.PartitionGroup.Type;
import io.atomix.primitive.partition.PartitionGroupConfig;
import io.atomix.raft.zeebe.EntryValidator;
import io.atomix.raft.zeebe.EntryValidator.NoopEntryValidator;
import java.util.HashSet;
import java.util.Set;

/** Raft partition group configuration. */
public class RaftPartitionGroupConfig extends PartitionGroupConfig<RaftPartitionGroupConfig> {

  private static final int DEFAULT_PARTITIONS = 7;

  private Set<String> members = new HashSet<>();
  private int replicationFactor;
  private RaftStorageConfig storageConfig = new RaftStorageConfig();
  private RaftPartitionConfig partitionConfig = new RaftPartitionConfig();

  @Optional("EntryValidator")
  private EntryValidator entryValidator = new NoopEntryValidator();

  @Override
  protected int getDefaultPartitions() {
    return DEFAULT_PARTITIONS;
  }

  /**
   * Returns the set of members in the partition group.
   *
   * @return the set of members in the partition group
   */
  public Set<String> getMembers() {
    return members;
  }

  /**
   * Sets the set of members in the partition group.
   *
   * @param members the set of members in the partition group
   * @return the Raft partition group configuration
   */
  public RaftPartitionGroupConfig setMembers(final Set<String> members) {
    this.members = members;
    return this;
  }

  /**
   * Returns the partition size.
   *
   * @return the partition size
   */
  public int getReplicationFactor() {
    return replicationFactor;
  }

  /**
   * Sets the partition size.
   *
   * @param replicationFactor the partition size
   * @return the Raft partition group configuration
   */
  public RaftPartitionGroupConfig setReplicationFactor(final int replicationFactor) {
    this.replicationFactor = replicationFactor;
    return this;
  }

  /**
   * Returns the storage configuration.
   *
   * @return the storage configuration
   */
  public RaftStorageConfig getStorageConfig() {
    return storageConfig;
  }

  /**
   * Sets the storage configuration.
   *
   * @param storageConfig the storage configuration
   * @return the Raft partition group configuration
   */
  public RaftPartitionGroupConfig setStorageConfig(final RaftStorageConfig storageConfig) {
    this.storageConfig = storageConfig;
    return this;
  }

  /**
   * Returns the entry validator to be called when an entry is appended.
   *
   * @return the entry validator
   */
  public EntryValidator getEntryValidator() {
    return entryValidator;
  }

  /**
   * Sets the entry validator to be called when an entry is appended.
   *
   * @param entryValidator the entry validator
   * @return the Raft Partition group builder
   */
  public RaftPartitionGroupConfig setEntryValidator(final EntryValidator entryValidator) {
    this.entryValidator = entryValidator;
    return this;
  }

  @Override
  public Type getType() {
    return RaftPartitionGroup.TYPE;
  }

  public RaftPartitionConfig getPartitionConfig() {
    return partitionConfig;
  }

  public RaftPartitionGroupConfig setPartitionConfig(final RaftPartitionConfig partitionConfig) {
    this.partitionConfig = partitionConfig;
    return this;
  }
}
