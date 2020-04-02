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
import io.atomix.cluster.protocol.GroupMembershipProtocol;
import io.atomix.cluster.protocol.GroupMembershipProtocolConfig;
import io.atomix.cluster.protocol.HeartbeatMembershipProtocol;
import io.atomix.cluster.protocol.HeartbeatMembershipProtocolConfig;
import io.atomix.utils.Builder;
import io.atomix.utils.net.Address;
import java.time.Duration;
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

  protected AtomixClusterBuilder() {
    this(new ClusterConfig());
  }

  protected AtomixClusterBuilder(final ClusterConfig config) {
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
   * communication. The format of the address can be {@code host:port} or just {@code host}.
   *
   * @param address a host:port tuple
   * @return the cluster builder
   * @throws io.atomix.utils.net.MalformedAddressException if a valid {@link Address} cannot be
   *     constructed from the arguments
   * @deprecated since 3.1. Use {@link #withHost(String)} and/or {@link #withPort(int)} instead
   */
  @Deprecated
  public AtomixClusterBuilder withAddress(final String address) {
    return withAddress(Address.from(address));
  }

  /**
   * Sets the member host/port.
   *
   * <p>The constructed {@link AtomixCluster} will bind to the given host/port for intra-cluster
   * communication. The provided host should be visible to other nodes in the cluster.
   *
   * @param host the host name
   * @param port the port number
   * @return the cluster builder
   * @throws io.atomix.utils.net.MalformedAddressException if a valid {@link Address} cannot be
   *     constructed from the arguments
   * @deprecated since 3.1. Use {@link #withHost(String)} and {@link #withPort(int)} instead
   */
  @Deprecated
  public AtomixClusterBuilder withAddress(final String host, final int port) {
    return withAddress(Address.from(host, port));
  }

  /**
   * Sets the member address using local host.
   *
   * <p>The constructed {@link AtomixCluster} will bind to the given port for intra-cluster
   * communication.
   *
   * @param port the port number
   * @return the cluster builder
   * @throws io.atomix.utils.net.MalformedAddressException if a valid {@link Address} cannot be
   *     constructed from the arguments
   * @deprecated since 3.1. Use {@link #withPort(int)} instead
   */
  @Deprecated
  public AtomixClusterBuilder withAddress(final int port) {
    return withAddress(Address.from(port));
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
   * Sets the zone to which the member belongs.
   *
   * <p>The zone attribute can be used to enable zone-awareness in replication for certain primitive
   * protocols. It is an arbitrary string that should be used to group multiple nodes together by
   * their physical location.
   *
   * @param zoneId the zone to which the member belongs
   * @return the cluster builder
   */
  public AtomixClusterBuilder withZoneId(final String zoneId) {
    config.getNodeConfig().setZoneId(zoneId);
    return this;
  }

  /**
   * Sets the zone to which the member belongs.
   *
   * <p>The zone attribute can be used to enable zone-awareness in replication for certain primitive
   * protocols. It is an arbitrary string that should be used to group multiple nodes together by
   * their physical location.
   *
   * @param zone the zone to which the member belongs
   * @return the cluster builder
   * @deprecated since 3.1. Use {@link #withZoneId(String)} instead
   */
  @Deprecated
  public AtomixClusterBuilder withZone(final String zone) {
    config.getNodeConfig().setZoneId(zone);
    return this;
  }

  /**
   * Sets the rack to which the member belongs.
   *
   * <p>The rack attribute can be used to enable rack-awareness in replication for certain primitive
   * protocols. It is an arbitrary string that should be used to group multiple nodes together by
   * their physical location.
   *
   * @param rackId the rack to which the member belongs
   * @return the cluster builder
   */
  public AtomixClusterBuilder withRackId(final String rackId) {
    config.getNodeConfig().setRackId(rackId);
    return this;
  }

  /**
   * Sets the rack to which the member belongs.
   *
   * <p>The rack attribute can be used to enable rack-awareness in replication for certain primitive
   * protocols. It is an arbitrary string that should be used to group multiple nodes together by
   * their physical location.
   *
   * @param rack the rack to which the member belongs
   * @return the cluster builder
   * @deprecated since 3.1. Use {@link #withRackId(String)} instead
   */
  @Deprecated
  public AtomixClusterBuilder withRack(final String rack) {
    config.getNodeConfig().setRackId(rack);
    return this;
  }

  /**
   * Sets the host to which the member belongs.
   *
   * <p>The host attribute can be used to enable host-awareness in replication for certain primitive
   * protocols. It is an arbitrary string that should be used to group multiple nodes together by
   * their physical location. Typically this attribute only applies to containerized clusters.
   *
   * @param hostId the host to which the member belongs
   * @return the cluster builder
   */
  public AtomixClusterBuilder withHostId(final String hostId) {
    config.getNodeConfig().setHostId(hostId);
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
   * Sets a property of the member.
   *
   * <p>The properties are arbitrary settings that will be replicated along with this node's member
   * information. Properties can be used to enable other nodes to determine metadata about this
   * node.
   *
   * @param key the property key to set
   * @param value the property value to set
   * @return the cluster builder
   * @throws NullPointerException if the property is null
   */
  public AtomixClusterBuilder withProperty(final String key, final String value) {
    config.getNodeConfig().setProperty(key, value);
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
  public AtomixClusterBuilder withMessagingInterfaces(final String... ifaces) {
    return withMessagingInterfaces(Lists.newArrayList(ifaces));
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
   * Sets the messaging connection pool size.
   *
   * <p>The node will create {@code connectionPoolSize} connections to each peer with which it
   * regularly communicates over TCP. Periodic heartbeats from cluster membership protocols will not
   * consume pool connections. Thus, if a node does not communicate with one of its peers for
   * replication or application communication, the pool for that peer should remain empty.
   *
   * @param connectionPoolSize the connection pool size
   * @return the cluster builder
   */
  public AtomixClusterBuilder withConnectionPoolSize(final int connectionPoolSize) {
    config.getMessagingConfig().setConnectionPoolSize(connectionPoolSize);
    return this;
  }

  /**
   * Enables multicast communication.
   *
   * <p>Multicast is disabled by default. This method must be called to enable it. Enabling
   * multicast enables the use of the {@link io.atomix.cluster.messaging.BroadcastService}.
   *
   * @return the cluster builder
   * @see #withMulticastAddress(Address)
   */
  public AtomixClusterBuilder withMulticastEnabled() {
    return withMulticastEnabled(true);
  }

  /**
   * Sets whether multicast communication is enabled.
   *
   * <p>Multicast is disabled by default. This method must be called to enable it. Enabling
   * multicast enables the use of the {@link io.atomix.cluster.messaging.BroadcastService}.
   *
   * @param multicastEnabled whether to enable multicast
   * @return the cluster builder
   * @see #withMulticastAddress(Address)
   */
  public AtomixClusterBuilder withMulticastEnabled(final boolean multicastEnabled) {
    config.getMulticastConfig().setEnabled(multicastEnabled);
    return this;
  }

  /**
   * Sets the multicast address.
   *
   * <p>Multicast is disabled by default. To enable multicast, first use {@link
   * #withMulticastEnabled()}.
   *
   * @param address the multicast address
   * @return the cluster builder
   */
  public AtomixClusterBuilder withMulticastAddress(final Address address) {
    config.getMulticastConfig().setGroup(address.address());
    config.getMulticastConfig().setPort(address.port());
    return this;
  }

  /**
   * Sets the reachability broadcast interval.
   *
   * <p>The broadcast interval is the interval at which heartbeats are sent to peers in the cluster.
   *
   * @param interval the reachability broadcast interval
   * @return the cluster builder
   * @deprecated since 3.0.2
   */
  @Deprecated
  public AtomixClusterBuilder setBroadcastInterval(final Duration interval) {
    final GroupMembershipProtocolConfig protocolConfig = config.getProtocolConfig();
    if (protocolConfig instanceof HeartbeatMembershipProtocolConfig) {
      ((HeartbeatMembershipProtocolConfig) protocolConfig).setHeartbeatInterval(interval);
    }
    return this;
  }

  /**
   * Sets the reachability broadcast interval.
   *
   * <p>The broadcast interval is the interval at which heartbeats are sent to peers in the cluster.
   *
   * @param interval the reachability broadcast interval
   * @return the cluster builder
   */
  @Deprecated
  public AtomixClusterBuilder withBroadcastInterval(final Duration interval) {
    final GroupMembershipProtocolConfig protocolConfig = config.getProtocolConfig();
    if (protocolConfig instanceof HeartbeatMembershipProtocolConfig) {
      ((HeartbeatMembershipProtocolConfig) protocolConfig).setHeartbeatInterval(interval);
    }
    return this;
  }

  /**
   * Sets the reachability failure detection threshold.
   *
   * <p>Reachability of cluster members is determined using a phi-accrual failure detector. The
   * reachability threshold is the phi threshold after which a peer will be determined to be
   * unreachable.
   *
   * @param threshold the reachability failure detection threshold
   * @return the cluster builder
   * @deprecated since 3.0.2
   */
  @Deprecated
  public AtomixClusterBuilder setReachabilityThreshold(final int threshold) {
    final GroupMembershipProtocolConfig protocolConfig = config.getProtocolConfig();
    if (protocolConfig instanceof HeartbeatMembershipProtocolConfig) {
      ((HeartbeatMembershipProtocolConfig) protocolConfig).setPhiFailureThreshold(threshold);
    }
    return this;
  }

  /**
   * Sets the reachability failure detection threshold.
   *
   * <p>Reachability of cluster members is determined using a phi-accrual failure detector. The
   * reachability threshold is the phi threshold after which a peer will be determined to be
   * unreachable.
   *
   * @param threshold the reachability failure detection threshold
   * @return the cluster builder
   */
  @Deprecated
  public AtomixClusterBuilder withReachabilityThreshold(final int threshold) {
    final GroupMembershipProtocolConfig protocolConfig = config.getProtocolConfig();
    if (protocolConfig instanceof HeartbeatMembershipProtocolConfig) {
      ((HeartbeatMembershipProtocolConfig) protocolConfig).setPhiFailureThreshold(threshold);
    }
    return this;
  }

  /**
   * Sets the reachability failure timeout.
   *
   * <p>The reachability timeout determines the maximum time after which a member will be marked
   * unreachable if heartbeats have failed.
   *
   * @param timeout the reachability failure timeout
   * @return the cluster builder
   */
  @Deprecated
  public AtomixClusterBuilder withReachabilityTimeout(final Duration timeout) {
    final GroupMembershipProtocolConfig protocolConfig = config.getProtocolConfig();
    if (protocolConfig instanceof HeartbeatMembershipProtocolConfig) {
      ((HeartbeatMembershipProtocolConfig) protocolConfig).setFailureTimeout(timeout);
    }
    return this;
  }

  /**
   * Sets the cluster membership protocol.
   *
   * <p>The membership protocol is responsible for determining the active set of members in the
   * cluster, replicating member metadata, and detecting failures. The default is {@link
   * HeartbeatMembershipProtocol}.
   *
   * @param protocol the cluster membership protocol
   * @return the cluster builder
   * @see HeartbeatMembershipProtocol
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
   * @see io.atomix.cluster.discovery.MulticastDiscoveryProvider
   */
  public AtomixClusterBuilder withMembershipProvider(final NodeDiscoveryProvider locationProvider) {
    config.setDiscoveryConfig(locationProvider.config());
    return this;
  }

  /**
   * Enables TLS for the Atomix messaging service.
   *
   * <p>The messaging service is the service through which all Atomix protocols communicate with
   * their peers. Enabling TLS for the messaging service enables TLS for all internal Atomix
   * communication. When TLS is enabled, Atomix will look for an {@code atomix.jks} file in the
   * {@code /conf} directory unless a keystore/truststore is provided.
   *
   * @return the cluster builder
   * @see #withKeyStore(String)
   * @see #withTrustStore(String)
   */
  public AtomixClusterBuilder withTlsEnabled() {
    return withTlsEnabled(true);
  }

  /**
   * Sets whether TLS is enabled for the Atomix messaging service.
   *
   * <p>The messaging service is the service through which all Atomix protocols communicate with
   * their peers. Enabling TLS for the messaging service enables TLS for all internal Atomix
   * communication. When TLS is enabled, Atomix will look for an {@code atomix.jks} file in the
   * {@code /conf} directory unless a keystore/truststore is provided.
   *
   * @return the cluster builder
   * @see #withKeyStore(String)
   * @see #withTrustStore(String)
   */
  public AtomixClusterBuilder withTlsEnabled(final boolean tlsEnabled) {
    config.getMessagingConfig().getTlsConfig().setEnabled(tlsEnabled);
    return this;
  }

  /**
   * Sets the key store to use for TLS in the Atomix messaging service.
   *
   * @param keyStore the key store path
   * @return the cluster builder
   */
  public AtomixClusterBuilder withKeyStore(final String keyStore) {
    config.getMessagingConfig().getTlsConfig().setKeyStore(keyStore);
    return this;
  }

  /**
   * Sets the key store password for the Atomix messaging service.
   *
   * @param keyStorePassword the key store password
   * @return the cluster builder
   */
  public AtomixClusterBuilder withKeyStorePassword(final String keyStorePassword) {
    config.getMessagingConfig().getTlsConfig().setKeyStorePassword(keyStorePassword);
    return this;
  }

  /**
   * Sets the trust store to use for TLS in the Atomix messaging service.
   *
   * @param trustStore the trust store path
   * @return the cluster builder
   */
  public AtomixClusterBuilder withTrustStore(final String trustStore) {
    config.getMessagingConfig().getTlsConfig().setTrustStore(trustStore);
    return this;
  }

  /**
   * Sets the trust store password for the Atomix messaging service.
   *
   * @param trustStorePassword the trust store password
   * @return the cluster builder
   */
  public AtomixClusterBuilder withTrustStorePassword(final String trustStorePassword) {
    config.getMessagingConfig().getTlsConfig().setTrustStorePassword(trustStorePassword);
    return this;
  }

  @Override
  public AtomixCluster build() {
    return new AtomixCluster(config, null);
  }
}
