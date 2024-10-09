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
package io.atomix.cluster;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.cluster.messaging.MessagingConfig.CompressionAlgorithm;
import io.atomix.cluster.protocol.GroupMembershipProtocol;
import io.atomix.utils.Builder;
import io.atomix.utils.Version;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.util.VersionUtil;
import java.io.File;
import java.util.Collection;
import java.util.Properties;

/**
 * Builder for an {@link AtomixCluster} instance.
 *
 * <p>This builder is used to configure an {@link AtomixCluster} instance programmatically. To
 * create a new builder, use one of the {@link AtomixCluster#builder()} static methods.
 *
 * <pre>{@code
 * AtomixClusterBuilder builder = AtomixCluster.builder();
 *
 * }</pre>
 *
 * The instance is configured by calling the {@code with*} methods on this builder. Once the
 * instance has been configured, call {@link #build()} to build the instance:
 *
 * <pre>{@code
 * AtomixCluster cluster = AtomixCluster.builder()
 *   .withMemberId("member-1")
 *   .withAddress("localhost", 5000)
 *   .build();
 *
 * }</pre>
 *
 * Backing the builder is an {@link ClusterConfig} which is loaded when the builder is initially
 * constructed. To load a configuration from a file, use {@link AtomixCluster#builder(String)}.
 */
public class AtomixClusterBuilder implements Builder<AtomixCluster> {

  protected final ClusterConfig config;
  private String schedulerPrefix;

  public AtomixClusterBuilder(final ClusterConfig config) {
    this.config = checkNotNull(config);
  }

  /**
   * Sets the cluster identifier.
   *
   * <p>The cluster identifier is used to verify intra-cluster communication is taking place between
   * nodes that are intended to be part of the same cluster, e.g. if multicast discovery is used. It
   * only needs to be configured if multiple Atomix clusters are running within the same network.
   *
   * @param clusterId the cluster identifier
   * @return the cluster builder
   */
  public AtomixClusterBuilder withClusterId(final String clusterId) {
    config.setClusterId(clusterId);
    return this;
  }

  /**
   * Sets the local member identifier.
   *
   * <p>The member identifier is an optional attribute that can be used to identify and send
   * messages directly to this node. If no member identifier is provided, a {@link java.util.UUID}
   * based identifier will be generated.
   *
   * @param localMemberId the local member identifier
   * @return the cluster builder
   */
  public AtomixClusterBuilder withMemberId(final String localMemberId) {
    config.getNodeConfig().setId(localMemberId);
    return this;
  }

  /**
   * Sets the local member identifier.
   *
   * <p>The member identifier is an optional attribute that can be used to identify and send
   * messages directly to this node. If no member identifier is provided, a {@link java.util.UUID}
   * based identifier will be generated.
   *
   * @param localMemberId the local member identifier
   * @return the cluster builder
   */
  public AtomixClusterBuilder withMemberId(final MemberId localMemberId) {
    config.getNodeConfig().setId(localMemberId);
    return this;
  }

  /**
   * Sets the member host.
   *
   * @param host the member host
   * @return the cluster builder
   */
  public AtomixClusterBuilder withHost(final String host) {
    config.getNodeConfig().setHost(host);
    return this;
  }

  /**
   * Sets the member port.
   *
   * @param port the member port
   * @return the cluster builder
   */
  public AtomixClusterBuilder withPort(final int port) {
    config.getNodeConfig().setPort(port);
    return this;
  }

  /**
   * Sets the member address.
   *
   * <p>The constructed {@link AtomixCluster} will bind to the given address for intra-cluster
   * communication. The provided address should be visible to other nodes in the cluster.
   *
   * @param address the member address
   * @return the cluster builder
   */
  public AtomixClusterBuilder withAddress(final Address address) {
    config.getNodeConfig().setAddress(address);
    return this;
  }

  /**
   * Sets the member properties.
   *
   * <p>The properties are arbitrary settings that will be replicated along with this node's member
   * information. Properties can be used to enable other nodes to determine metadata about this
   * node.
   *
   * @param properties the member properties
   * @return the cluster builder
   * @throws NullPointerException if the properties are null
   */
  public AtomixClusterBuilder withProperties(final Properties properties) {
    config.getNodeConfig().setProperties(properties);
    return this;
  }

  /**
   * Sets the interface to which to bind the instance.
   *
   * @param iface the interface to which to bind the instance
   * @return the cluster builder
   */
  public AtomixClusterBuilder withMessagingInterface(final String iface) {
    return withMessagingInterfaces(Lists.newArrayList(iface));
  }

  /**
   * Sets the interface(s) to which to bind the instance.
   *
   * @param ifaces the interface(s) to which to bind the instance
   * @return the cluster builder
   */
  public AtomixClusterBuilder withMessagingInterfaces(final Collection<String> ifaces) {
    config.getMessagingConfig().setInterfaces(Lists.newArrayList(ifaces));
    return this;
  }

  /**
   * Sets the local port to which to bind the node.
   *
   * @param bindPort the local port to which to bind the node
   * @return the cluster builder
   */
  public AtomixClusterBuilder withMessagingPort(final int bindPort) {
    config.getMessagingConfig().setPort(bindPort);
    return this;
  }

  /**
   * Sets the cluster membership protocol.
   *
   * <p>The membership protocol is responsible for determining the active set of members in the
   * cluster, replicating member metadata, and detecting failures.
   *
   * @param protocol the cluster membership protocol
   * @return the cluster builder
   * @see io.atomix.cluster.protocol.SwimMembershipProtocol
   */
  public AtomixClusterBuilder withMembershipProtocol(final GroupMembershipProtocol protocol) {
    config.setProtocolConfig(protocol.config());
    return this;
  }

  /**
   * Sets the cluster membership provider.
   *
   * <p>The membership provider determines how peers are located and the cluster is bootstrapped.
   *
   * @param locationProvider the membership provider
   * @return the cluster builder
   * @see io.atomix.cluster.discovery.BootstrapDiscoveryProvider
   */
  public AtomixClusterBuilder withMembershipProvider(final NodeDiscoveryProvider locationProvider) {
    config.setDiscoveryConfig(locationProvider.config());
    return this;
  }

  /**
   * Enables TLS encryption of the messaging service.
   *
   * @param certificateChain the certificate chain to use
   * @param privateKey the private key of the chain
   * @return the cluster builder
   * @see io.atomix.cluster.messaging.MessagingConfig#setTlsEnabled(boolean)
   * @see io.atomix.cluster.messaging.MessagingConfig#setCertificateChain(File)
   * @see io.atomix.cluster.messaging.MessagingConfig#setPrivateKey(File)
   */
  public AtomixClusterBuilder withSecurity(final File certificateChain, final File privateKey) {
    config
        .getMessagingConfig()
        .setTlsEnabled(true)
        .setCertificateChain(certificateChain)
        .setPrivateKey(privateKey);

    return this;
  }

  public AtomixClusterBuilder withMessageCompression(
      final CompressionAlgorithm messageCompression) {
    config.getMessagingConfig().setCompressionAlgorithm(messageCompression);

    return this;
  }

  public AtomixClusterBuilder withSchedulerPrefix(final String schedulerPrefix) {
    this.schedulerPrefix = schedulerPrefix;
    return this;
  }

  @Override
  public AtomixCluster build() {
    return new AtomixCluster(config, Version.from(VersionUtil.getVersion()), schedulerPrefix);
  }
}
