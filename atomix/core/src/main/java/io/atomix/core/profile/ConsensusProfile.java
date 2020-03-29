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
package io.atomix.core.profile;

import com.google.common.collect.Sets;
import io.atomix.core.AtomixConfig;
import io.atomix.raft.partition.RaftPartitionGroupConfig;
import io.atomix.raft.partition.RaftStorageConfig;
import java.util.Collection;

/** Consensus profile. */
public class ConsensusProfile implements Profile {
  public static final Type TYPE = new Type();
  private final ConsensusProfileConfig config;

  ConsensusProfile(final String... members) {
    this(Sets.newHashSet(members));
  }

  ConsensusProfile(final Collection<String> members) {
    this(new ConsensusProfileConfig().setMembers(Sets.newHashSet(members)));
  }

  ConsensusProfile(final ConsensusProfileConfig config) {
    this.config = config;
  }

  /**
   * Creates a new consensus profile builder.
   *
   * @return a new consensus profile builder
   */
  public static ConsensusProfileBuilder builder() {
    return new ConsensusProfileBuilder();
  }

  @Override
  public ConsensusProfileConfig config() {
    return config;
  }

  @Override
  public void configure(final AtomixConfig config) {
    config.setManagementGroup(
        new RaftPartitionGroupConfig()
            .setName(this.config.getManagementGroup())
            .setPartitionSize(this.config.getMembers().size())
            .setPartitions(1)
            .setMembers(this.config.getMembers())
            .setStorageConfig(
                new RaftStorageConfig()
                    .setDirectory(
                        String.format(
                            "%s/%s",
                            this.config.getDataPath(), this.config.getManagementGroup()))));
    config.addPartitionGroup(
        new RaftPartitionGroupConfig()
            .setName(this.config.getDataGroup())
            .setPartitionSize(this.config.getPartitionSize())
            .setPartitions(this.config.getPartitions())
            .setMembers(this.config.getMembers())
            .setStorageConfig(
                new RaftStorageConfig()
                    .setDirectory(
                        String.format(
                            "%s/%s", this.config.getDataPath(), this.config.getDataGroup()))));
  }

  /** Consensus profile type. */
  public static class Type implements Profile.Type<ConsensusProfileConfig> {
    private static final String NAME = "consensus";

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public ConsensusProfileConfig newConfig() {
      return new ConsensusProfileConfig();
    }

    @Override
    public Profile newProfile(final ConsensusProfileConfig config) {
      return new ConsensusProfile(config);
    }
  }
}
