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
package io.atomix.core;

import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.ClusterConfig;
import io.atomix.core.profile.ProfileConfig;
import io.atomix.primitive.partition.PartitionGroupConfig;
import io.atomix.utils.config.Config;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Atomix configuration. */
public class AtomixConfig implements Config {
  private ClusterConfig cluster = new ClusterConfig();
  private boolean enableShutdownHook;
  private Map<String, PartitionGroupConfig<?>> partitionGroups = new HashMap<>();
  private List<ProfileConfig> profiles = new ArrayList<>();
  private boolean typeRegistrationRequired = false;
  private boolean compatibleSerialization = false;

  /**
   * Returns the cluster configuration.
   *
   * @return the cluster configuration
   */
  public ClusterConfig getClusterConfig() {
    return cluster;
  }

  /**
   * Sets the cluster configuration.
   *
   * @param cluster the cluster configuration
   * @return the Atomix configuration
   */
  public AtomixConfig setClusterConfig(final ClusterConfig cluster) {
    this.cluster = cluster;
    return this;
  }

  /**
   * Returns whether to enable the shutdown hook.
   *
   * @return whether to enable the shutdown hook
   */
  public boolean isEnableShutdownHook() {
    return enableShutdownHook;
  }

  /**
   * Sets whether to enable the shutdown hook.
   *
   * @param enableShutdownHook whether to enable the shutdown hook
   * @return the Atomix configuration
   */
  public AtomixConfig setEnableShutdownHook(final boolean enableShutdownHook) {
    this.enableShutdownHook = enableShutdownHook;
    return this;
  }

  /**
   * Returns the partition group configurations.
   *
   * @return the partition group configurations
   */
  public Map<String, PartitionGroupConfig<?>> getPartitionGroups() {
    return partitionGroups;
  }

  /**
   * Sets the partition group configurations.
   *
   * @param partitionGroups the partition group configurations
   * @return the Atomix configuration
   */
  public AtomixConfig setPartitionGroups(
      final Map<String, PartitionGroupConfig<?>> partitionGroups) {
    partitionGroups.forEach((name, group) -> group.setName(name));
    this.partitionGroups = partitionGroups;
    return this;
  }

  /**
   * Adds a partition group configuration.
   *
   * @param partitionGroup the partition group configuration to add
   * @return the Atomix configuration
   */
  public AtomixConfig addPartitionGroup(final PartitionGroupConfig partitionGroup) {
    partitionGroups.put(partitionGroup.getName(), partitionGroup);
    return this;
  }

  /**
   * Returns the Atomix profile.
   *
   * @return the Atomix profile
   */
  public List<ProfileConfig> getProfiles() {
    return profiles;
  }

  /**
   * Sets the Atomix profile.
   *
   * @param profiles the profiles
   * @return the Atomix configuration
   */
  public AtomixConfig setProfiles(final List<ProfileConfig> profiles) {
    this.profiles = profiles;
    return this;
  }

  /**
   * Adds an Atomix profile.
   *
   * @param profile the profile to add
   * @return the Atomix configuration
   */
  public AtomixConfig addProfile(final ProfileConfig profile) {
    profiles.add(checkNotNull(profile, "profile cannot be null"));
    return this;
  }

  /**
   * Returns whether serializable type registration is required for user types.
   *
   * @return whether serializable type registration is required for user types
   */
  public boolean isTypeRegistrationRequired() {
    return typeRegistrationRequired;
  }

  /**
   * Sets whether serializable type registration is required for user types.
   *
   * @param typeRegistrationRequired whether serializable type registration is required for user
   *     types
   * @return the Atomix configuration
   */
  public AtomixConfig setTypeRegistrationRequired(final boolean typeRegistrationRequired) {
    this.typeRegistrationRequired = typeRegistrationRequired;
    return this;
  }

  /**
   * Returns whether compatible serialization is enabled for user types.
   *
   * @return whether compatible serialization is enabled for user types
   */
  public boolean isCompatibleSerialization() {
    return compatibleSerialization;
  }

  /**
   * Sets whether compatible serialization is enabled for user types.
   *
   * @param compatibleSerialization whether compatible serialization is enabled for user types
   * @return the Atomix configuration
   */
  public AtomixConfig setCompatibleSerialization(final boolean compatibleSerialization) {
    this.compatibleSerialization = compatibleSerialization;
    return this;
  }
}
