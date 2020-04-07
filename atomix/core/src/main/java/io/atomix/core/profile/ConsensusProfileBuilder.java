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
import java.io.File;
import java.util.Set;

/** Consensus profile builder. */
public class ConsensusProfileBuilder extends ProfileBuilder {
  private final ConsensusProfileConfig config = new ConsensusProfileConfig();

  ConsensusProfileBuilder() {}

  /**
   * Sets the consensus data file path.
   *
   * @param dataPath the consensus data file path
   * @return the consensus profile builder
   */
  public ConsensusProfileBuilder withDataPath(final String dataPath) {
    config.setDataPath(dataPath);
    return this;
  }

  /**
   * Sets the consensus data file path.
   *
   * @param dataPath the consensus data file path
   * @return the consensus profile builder
   */
  public ConsensusProfileBuilder withDataPath(final File dataPath) {
    config.setDataPath(dataPath.getPath());
    return this;
  }

  /**
   * Sets the management partition group name.
   *
   * @param managementGroup the management partition group name
   * @return the consensus profile builder
   */
  public ConsensusProfileBuilder withManagementGroup(final String managementGroup) {
    config.setManagementGroup(managementGroup);
    return this;
  }

  /**
   * Sets the data partition group name.
   *
   * @param dataGroup the data partition group name
   * @return the consensus profile builder
   */
  public ConsensusProfileBuilder withDataGroup(final String dataGroup) {
    config.setDataGroup(dataGroup);
    return this;
  }

  /**
   * Sets the data partition size.
   *
   * @param partitionSize the data partition size
   * @return the consensus profile builder
   */
  public ConsensusProfileBuilder withPartitionSize(final int partitionSize) {
    config.setPartitionSize(partitionSize);
    return this;
  }

  /**
   * Sets the number of data partitions to configure.
   *
   * @param numPartitions the number of data partitions to configure
   * @return the consensus profile builder
   */
  public ConsensusProfileBuilder withNumPartitions(final int numPartitions) {
    config.setPartitions(numPartitions);
    return this;
  }

  /**
   * Sets the consensus members.
   *
   * @param members the consensus members
   * @return the profile builder
   */
  public ConsensusProfileBuilder withMembers(final String... members) {
    return withMembers(Sets.newHashSet(members));
  }

  /**
   * Sets the consensus members.
   *
   * @param members the consensus members
   * @return the profile builder
   */
  public ConsensusProfileBuilder withMembers(final Set<String> members) {
    config.setMembers(members);
    return this;
  }

  @Override
  public Profile build() {
    return new ConsensusProfile(config);
  }
}
