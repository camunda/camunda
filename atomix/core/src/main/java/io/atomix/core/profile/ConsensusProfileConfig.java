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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Set;

/** Consensus profile configuration. */
public class ConsensusProfileConfig extends ProfileConfig {
  private String dataPath = System.getProperty("atomix.data", ".data");
  private String managementGroup = "system";
  private String dataGroup = "raft";
  private int partitionSize = 3;
  private int partitions = 7;
  private Set<String> members = Collections.emptySet();

  @Override
  public Profile.Type getType() {
    return ConsensusProfile.TYPE;
  }

  /**
   * Returns the data file path.
   *
   * @return the consensus data file path
   */
  public String getDataPath() {
    return dataPath;
  }

  /**
   * Sets the consensus data file path.
   *
   * @param dataPath the consensus data file path
   * @return the consensus profile configuration
   */
  public ConsensusProfileConfig setDataPath(final String dataPath) {
    this.dataPath = checkNotNull(dataPath);
    return this;
  }

  /**
   * Returns the management partition group name.
   *
   * @return the management partition group name
   */
  public String getManagementGroup() {
    return managementGroup;
  }

  /**
   * Sets the management partition group name.
   *
   * @param managementGroup the management partition group name
   * @return the consensus profile configurations
   */
  public ConsensusProfileConfig setManagementGroup(final String managementGroup) {
    this.managementGroup = checkNotNull(managementGroup);
    return this;
  }

  /**
   * Returns the data partition group name.
   *
   * @return the data partition group name
   */
  public String getDataGroup() {
    return dataGroup;
  }

  /**
   * Sets the data partition group name.
   *
   * @param dataGroup the data partition group name
   * @return the consensus profile configurations
   */
  public ConsensusProfileConfig setDataGroup(final String dataGroup) {
    this.dataGroup = checkNotNull(dataGroup);
    return this;
  }

  /**
   * Returns the data partition size.
   *
   * @return the data partition size
   */
  public int getPartitionSize() {
    return partitionSize;
  }

  /**
   * Sets the data partition size.
   *
   * @param partitionSize the data partition size
   * @return the consensus profile configurations
   */
  public ConsensusProfileConfig setPartitionSize(final int partitionSize) {
    checkArgument(partitionSize > 0, "partitionSize must be positive");
    this.partitionSize = partitionSize;
    return this;
  }

  /**
   * Returns the number of data partitions to configure.
   *
   * @return the number of data partitions to configure
   */
  public int getPartitions() {
    return partitions;
  }

  /**
   * Sets the number of data partitions to configure.
   *
   * @param partitions the number of data partitions to configure
   * @return the consensus profile configurations
   */
  public ConsensusProfileConfig setPartitions(final int partitions) {
    checkArgument(partitions > 0, "partitions must be positive");
    this.partitions = partitions;
    return this;
  }

  /**
   * Returns the consensus members.
   *
   * @return the consensus members
   */
  public Set<String> getMembers() {
    return members;
  }

  /**
   * Sets the consensus members.
   *
   * @param members the consensus members
   * @return the profile configuration
   */
  public ConsensusProfileConfig setMembers(final Set<String> members) {
    this.members = members;
    return this;
  }
}
