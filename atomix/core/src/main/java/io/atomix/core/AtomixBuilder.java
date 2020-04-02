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

import io.atomix.cluster.AtomixClusterBuilder;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.cluster.protocol.GroupMembershipProtocol;
import io.atomix.core.profile.Profile;
import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.utils.net.Address;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

/**
 * Builder for {@link Atomix} instance.
 *
 * <p>This builder is used to configure an {@link Atomix} instance programmatically. To create a new
 * builder, use one of the {@link Atomix#builder()} static methods.
 *
 * <pre>{@code
 * AtomixBuilder builder = Atomix.builder();
 *
 * }</pre>
 *
 * The instance is configured by calling the {@code with*} methods on this builder. Once the
 * instance has been configured, call {@link #build()} to build the instance:
 *
 * <pre>{@code
 * Atomix atomix = Atomix.builder()
 *   .withMemberId("member-1")
 *   .withHost("192.168.10.2")
 *   .withPort(5000)
 *   .build();
 *
 * }</pre>
 *
 * Backing the builder is an {@link AtomixConfig} which is loaded when the builder is initially
 * constructed. To load a configuration from a file, use {@link Atomix#builder(String)}.
 */
public class AtomixBuilder extends AtomixClusterBuilder {
  private final AtomixConfig config;
  private final AtomixRegistry registry;

  protected AtomixBuilder(final AtomixConfig config, final AtomixRegistry registry) {
    super(config.getClusterConfig());
    this.config = checkNotNull(config);
    this.registry = checkNotNull(registry);
  }

  /**
   * Enables the shutdown hook.
   *
   * <p>When the shutdown hook is enabled, the instance will be shutdown when the JVM exits.
   *
   * @return the Atomix builder
   */
  public AtomixBuilder withShutdownHookEnabled() {
    return withShutdownHook(true);
  }

  /**
   * Sets whether the shutdown hook is enabled.
   *
   * <p>When the shutdown hook is enabled, the instance will be shutdown when the JVM exits.
   *
   * @param enabled if <code>true</code> a shutdown hook will be registered
   * @return the Atomix builder
   */
  public AtomixBuilder withShutdownHook(final boolean enabled) {
    config.setEnableShutdownHook(enabled);
    return this;
  }

  /**
   * Sets the Atomix profiles.
   *
   * <p>Profiles are common configurations that will be applied to the instance configuration when
   * the {@link Atomix} instance is constructed.
   *
   * @param profiles the profiles
   * @return the Atomix builder
   * @throws NullPointerException if the profiles are null
   */
  public AtomixBuilder withProfiles(final Profile... profiles) {
    return withProfiles(Arrays.asList(checkNotNull(profiles)));
  }

  /**
   * Sets the Atomix profiles.
   *
   * <p>Profiles are common configurations that will be applied to the instance configuration when
   * the {@link Atomix} instance is constructed.
   *
   * @param profiles the profiles
   * @return the Atomix builder
   * @throws NullPointerException if the profiles are null
   */
  public AtomixBuilder withProfiles(final Collection<Profile> profiles) {
    profiles.forEach(profile -> config.addProfile(profile.config()));
    return this;
  }

  /**
   * Adds an Atomix profile.
   *
   * <p>Profiles are common configurations that will be applied to the instance configuration when
   * the {@link Atomix} instance is constructed.
   *
   * @param profile the profile to add
   * @return the Atomix builder
   * @throws NullPointerException if the profile is null
   */
  public AtomixBuilder addProfile(final Profile profile) {
    config.addProfile(profile.config());
    return this;
  }

  /**
   * Sets the system management partition group.
   *
   * <p>The system management group must be configured for stateful instances. This group will be
   * used to store primitive and transaction metadata and coordinate primary elections for
   * replication protocols.
   *
   * <pre>{@code
   * Atomix atomix = Atomix.builder()
   *   .withManagementGroup(RaftPartitionGroup.builder("system")
   *     .withNumPartitions(1)
   *     .build())
   *   .build();
   *
   * }</pre>
   *
   * <p>The configured partition group is replicated on whichever nodes define them in this
   * configuration. That is, this node will participate in whichever partition group is provided to
   * this method.
   *
   * <p>The management group can also be configured in {@code atomix.conf} under the {@code
   * management-group} key.
   *
   * @param systemManagementGroup the system management partition group
   * @return the Atomix builder
   */
  public AtomixBuilder withManagementGroup(final ManagedPartitionGroup systemManagementGroup) {
    config.setManagementGroup(systemManagementGroup.config());
    return this;
  }

  /**
   * Sets the primitive partition groups.
   *
   * <p>The primitive partition groups represent partitions that are directly accessible to
   * distributed primitives. To use partitioned primitives, at least one node must be configured
   * with at least one data partition group.
   *
   * <pre>{@code
   * Atomix atomix = Atomix.builder()
   *   .withPartitionGroups(PrimaryBackupPartitionGroup.builder("data")
   *     .withNumPartitions(32)
   *     .build())
   *   .build();
   *
   * }</pre>
   *
   * The partition group name is used to uniquely identify the group when constructing primitive
   * instances. Partitioned primitives will reference a specific protocol and partition group within
   * which to replicate the primitive.
   *
   * <p>The configured partition groups are replicated on whichever nodes define them in this
   * configuration. That is, this node will participate in whichever partition groups are provided
   * to this method.
   *
   * <p>The partition groups can also be configured in {@code atomix.conf} under the {@code
   * partition-groups} key.
   *
   * @param partitionGroups the partition groups
   * @return the Atomix builder
   * @throws NullPointerException if the partition groups are null
   */
  public AtomixBuilder withPartitionGroups(final ManagedPartitionGroup... partitionGroups) {
    return withPartitionGroups(
        Arrays.asList(checkNotNull(partitionGroups, "partitionGroups cannot be null")));
  }

  /**
   * Sets the primitive partition groups.
   *
   * <p>The primitive partition groups represent partitions that are directly accessible to
   * distributed primitives. To use partitioned primitives, at least one node must be configured
   * with at least one data partition group.
   *
   * <pre>{@code
   * Atomix atomix = Atomix.builder()
   *   .withPartitionGroups(PrimaryBackupPartitionGroup.builder("data")
   *     .withNumPartitions(32)
   *     .build())
   *   .build();
   *
   * }</pre>
   *
   * The partition group name is used to uniquely identify the group when constructing primitive
   * instances. Partitioned primitives will reference a specific protocol and partition group within
   * which to replicate the primitive.
   *
   * <p>The configured partition groups are replicated on whichever nodes define them in this
   * configuration. That is, this node will participate in whichever partition groups are provided
   * to this method.
   *
   * <p>The partition groups can also be configured in {@code atomix.conf} under the {@code
   * partition-groups} key.
   *
   * @param partitionGroups the partition groups
   * @return the Atomix builder
   * @throws NullPointerException if the partition groups are null
   */
  public AtomixBuilder withPartitionGroups(
      final Collection<ManagedPartitionGroup> partitionGroups) {
    partitionGroups.forEach(group -> config.addPartitionGroup(group.config()));
    return this;
  }

  /**
   * Adds a primitive partition group.
   *
   * <p>The provided group will be added to the list of already configured partition groups. The
   * primitive partition groups represent partitions that are directly accessible to distributed
   * primitives. To use partitioned primitives, at least one node must be configured with at least
   * one data partition group.
   *
   * <pre>{@code
   * Atomix atomix = Atomix.builder()
   *   .withPartitionGroups(PrimaryBackupPartitionGroup.builder("data")
   *     .withNumPartitions(32)
   *     .build())
   *   .build();
   *
   * }</pre>
   *
   * The partition group name is used to uniquely identify the group when constructing primitive
   * instances. Partitioned primitives will reference a specific protocol and partition group within
   * which to replicate the primitive.
   *
   * <p>The configured partition groups are replicated on whichever nodes define them in this
   * configuration. That is, this node will participate in whichever partition groups are provided
   * to this method.
   *
   * <p>The partition groups can also be configured in {@code atomix.conf} under the {@code
   * partition-groups} key.
   *
   * @param partitionGroup the partition group to add
   * @return the Atomix builder
   * @throws NullPointerException if the partition group is null
   */
  public AtomixBuilder addPartitionGroup(final ManagedPartitionGroup partitionGroup) {
    config.addPartitionGroup(partitionGroup.config());
    return this;
  }

  /**
   * Requires explicit serializable type registration for user types.
   *
   * @return the Atomix builder
   */
  public AtomixBuilder withTypeRegistrationRequired() {
    return withTypeRegistrationRequired(true);
  }

  /**
   * Sets whether serializable type registration is required for user types.
   *
   * @param required whether serializable type registration is required for user types
   * @return the Atomix builder
   */
  public AtomixBuilder withTypeRegistrationRequired(final boolean required) {
    config.setTypeRegistrationRequired(required);
    return this;
  }

  /**
   * Enables compatible serialization for user types.
   *
   * @return the Atomix builder
   */
  public AtomixBuilder withCompatibleSerialization() {
    return withCompatibleSerialization(true);
  }

  /**
   * Sets whether compatible serialization is enabled for user types.
   *
   * @param enabled whether compatible serialization is enabled for user types
   * @return the Atomix builder
   */
  public AtomixBuilder withCompatibleSerialization(final boolean enabled) {
    config.setCompatibleSerialization(enabled);
    return this;
  }

  @Override
  public AtomixBuilder withClusterId(final String clusterId) {
    super.withClusterId(clusterId);
    return this;
  }

  @Override
  public AtomixBuilder withMemberId(final String localMemberId) {
    super.withMemberId(localMemberId);
    return this;
  }

  @Override
  public AtomixBuilder withMemberId(final MemberId localMemberId) {
    super.withMemberId(localMemberId);
    return this;
  }

  @Override
  public AtomixBuilder withHost(final String host) {
    super.withHost(host);
    return this;
  }

  @Override
  public AtomixBuilder withPort(final int port) {
    super.withPort(port);
    return this;
  }

  @Override
  @Deprecated
  public AtomixBuilder withAddress(final String address) {
    super.withAddress(address);
    return this;
  }

  @Override
  @Deprecated
  public AtomixBuilder withAddress(final String host, final int port) {
    super.withAddress(host, port);
    return this;
  }

  @Override
  @Deprecated
  public AtomixBuilder withAddress(final int port) {
    super.withAddress(port);
    return this;
  }

  @Override
  public AtomixBuilder withAddress(final Address address) {
    super.withAddress(address);
    return this;
  }

  @Override
  public AtomixBuilder withZoneId(final String zoneId) {
    super.withZoneId(zoneId);
    return this;
  }

  @Override
  @Deprecated
  public AtomixBuilder withZone(final String zone) {
    super.withZone(zone);
    return this;
  }

  @Override
  public AtomixBuilder withRackId(final String rackId) {
    super.withRackId(rackId);
    return this;
  }

  @Override
  @Deprecated
  public AtomixBuilder withRack(final String rack) {
    super.withRack(rack);
    return this;
  }

  @Override
  public AtomixBuilder withHostId(final String hostId) {
    super.withHostId(hostId);
    return this;
  }

  @Override
  public AtomixBuilder withProperties(final Properties properties) {
    super.withProperties(properties);
    return this;
  }

  @Override
  public AtomixBuilder withProperty(final String key, final String value) {
    super.withProperty(key, value);
    return this;
  }

  @Override
  public AtomixBuilder withMessagingInterface(final String iface) {
    super.withMessagingInterface(iface);
    return this;
  }

  @Override
  public AtomixBuilder withMessagingInterfaces(final String... ifaces) {
    super.withMessagingInterfaces(ifaces);
    return this;
  }

  @Override
  public AtomixBuilder withMessagingInterfaces(final Collection<String> ifaces) {
    super.withMessagingInterfaces(ifaces);
    return this;
  }

  @Override
  public AtomixBuilder withMessagingPort(final int bindPort) {
    super.withMessagingPort(bindPort);
    return this;
  }

  @Override
  public AtomixBuilder withConnectionPoolSize(final int connectionPoolSize) {
    super.withConnectionPoolSize(connectionPoolSize);
    return this;
  }

  @Override
  public AtomixBuilder withMulticastEnabled() {
    super.withMulticastEnabled();
    return this;
  }

  @Override
  public AtomixBuilder withMulticastEnabled(final boolean multicastEnabled) {
    super.withMulticastEnabled(multicastEnabled);
    return this;
  }

  @Override
  public AtomixBuilder withMulticastAddress(final Address address) {
    super.withMulticastAddress(address);
    return this;
  }

  @Override
  public AtomixBuilder setBroadcastInterval(final Duration interval) {
    super.setBroadcastInterval(interval);
    return this;
  }

  @Override
  public AtomixBuilder setReachabilityThreshold(final int threshold) {
    super.setReachabilityThreshold(threshold);
    return this;
  }

  @Override
  public AtomixBuilder withReachabilityTimeout(final Duration timeout) {
    super.withReachabilityTimeout(timeout);
    return this;
  }

  @Override
  public AtomixBuilder withMembershipProtocol(final GroupMembershipProtocol protocol) {
    super.withMembershipProtocol(protocol);
    return this;
  }

  @Override
  public AtomixBuilder withMembershipProvider(final NodeDiscoveryProvider locationProvider) {
    super.withMembershipProvider(locationProvider);
    return this;
  }

  @Override
  public AtomixBuilder withTlsEnabled() {
    super.withTlsEnabled();
    return this;
  }

  @Override
  public AtomixBuilder withTlsEnabled(final boolean tlsEnabled) {
    super.withTlsEnabled(tlsEnabled);
    return this;
  }

  @Override
  public AtomixBuilder withKeyStore(final String keyStore) {
    super.withKeyStore(keyStore);
    return this;
  }

  @Override
  public AtomixBuilder withKeyStorePassword(final String keyStorePassword) {
    super.withKeyStorePassword(keyStorePassword);
    return this;
  }

  @Override
  public AtomixBuilder withTrustStore(final String trustStore) {
    super.withTrustStore(trustStore);
    return this;
  }

  @Override
  public AtomixBuilder withTrustStorePassword(final String trustStorePassword) {
    super.withTrustStorePassword(trustStorePassword);
    return this;
  }

  /**
   * Builds a new Atomix instance.
   *
   * <p>The returned instance will be configured with the initial builder configuration plus any
   * overrides that were made via the builder. The returned instance will not be running. To start
   * the instance call the {@link Atomix#start()} method:
   *
   * <pre>{@code
   * Atomix atomix = Atomix.builder()
   *   .withMemberId("member-1")
   *   .withHost("192.168.10.2")
   *   .withPort(5000)
   *   .build();
   * atomix.start().join();
   *
   * }</pre>
   *
   * @return a new Atomix instance
   */
  @Override
  public Atomix build() {
    return new Atomix(config, registry);
  }
}
