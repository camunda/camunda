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

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.discovery.NodeDiscoveryConfig;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.cluster.impl.DefaultClusterMembershipService;
import io.atomix.cluster.impl.DefaultNodeDiscoveryService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.ManagedClusterCommunicationService;
import io.atomix.cluster.messaging.ManagedClusterEventService;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.ManagedUnicastService;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.messaging.UnicastService;
import io.atomix.cluster.messaging.impl.DefaultClusterCommunicationService;
import io.atomix.cluster.messaging.impl.DefaultClusterEventService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.cluster.messaging.impl.NettyUnicastService;
import io.atomix.cluster.protocol.GroupMembershipProtocol;
import io.atomix.utils.Managed;
import io.atomix.utils.Version;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.net.Address;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Atomix cluster manager.
 *
 * <p>The cluster manager is the basis for all cluster management and communication in an Atomix
 * cluster. This class is responsible for bootstrapping new clusters or joining existing ones,
 * establishing communication between nodes, and detecting failures.
 *
 * <p>The Atomix cluster can be run as a standalone instance for cluster management and
 * communication. To build a cluster instance, use {@link #builder(MeterRegistry)} to create a new
 * builder.
 *
 * <pre>{@code
 * AtomixCluster cluster = AtomixCluster.builder()
 *   .withClusterName("my-cluster")
 *   .withMemberId("member-1")
 *   .withAddress("localhost:1234")
 *   .withMulticastEnabled()
 *   .build();
 *
 * }</pre>
 *
 * The instance can be configured with a unique identifier via {@link
 * AtomixClusterBuilder#withMemberId(String)}. The member ID can be used to lookup the member in the
 * {@link ClusterMembershipService} or send messages to this node from other member nodes. The
 * {@link AtomixClusterBuilder#withAddress(Address) address} is the host and port to which the node
 * will bind for intra-cluster communication over TCP.
 *
 * <p>Once an instance has been configured, the {@link #start()} method must be called to bootstrap
 * the instance. The {@code start()} method returns a {@link CompletableFuture} which will be
 * completed once all the services have been bootstrapped.
 *
 * <pre>{@code
 * cluster.start().join();
 *
 * }</pre>
 *
 * <p>Cluster membership is determined by a configurable {@link NodeDiscoveryProvider}. To configure
 * the membership provider use {@link
 * AtomixClusterBuilder#withMembershipProvider(NodeDiscoveryProvider)}. The {@link
 * BootstrapDiscoveryProvider} will be used if no provider is explicitly provided.
 */
public class AtomixCluster implements BootstrapService, Managed<Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AtomixCluster.class);
  protected final ManagedMessagingService messagingService;
  protected final ManagedUnicastService unicastService;
  protected final NodeDiscoveryProvider discoveryProvider;
  protected final GroupMembershipProtocol membershipProtocol;
  protected final ManagedClusterMembershipService membershipService;
  protected final ManagedClusterCommunicationService communicationService;
  protected final ManagedClusterEventService eventService;
  protected volatile CompletableFuture<Void> openFuture;
  protected volatile CompletableFuture<Void> closeFuture;
  protected final ThreadContext threadContext = new SingleThreadContext("atomix-cluster-%d");
  private final AtomicBoolean started = new AtomicBoolean();

  public AtomixCluster(
      final ClusterConfig config,
      final Version version,
      final String actorSchedulerName,
      final MeterRegistry registry) {
    this(config, version, null, null, actorSchedulerName, registry);
  }

  protected AtomixCluster(
      final ClusterConfig config,
      final Version version,
      final ManagedMessagingService messagingService,
      final ManagedUnicastService unicastService,
      final String actorSchedulerName,
      final MeterRegistry registry) {
    this.messagingService =
        messagingService != null
            ? messagingService
            : buildMessagingService(config, actorSchedulerName, registry);
    this.unicastService =
        unicastService != null
            ? unicastService
            : buildUnicastService(config, actorSchedulerName, registry);

    discoveryProvider = buildLocationProvider(config);
    membershipProtocol = buildMembershipProtocol(config, actorSchedulerName, registry);
    membershipService =
        buildClusterMembershipService(config, this, discoveryProvider, membershipProtocol, version);
    communicationService =
        buildClusterMessagingService(
            getMembershipService(), getMessagingService(), getUnicastService());
    eventService = buildClusterEventService(getMembershipService(), getMessagingService());
  }

  /**
   * Returns a new Atomix builder.
   *
   * @return a new Atomix builder
   */
  public static AtomixClusterBuilder builder(final MeterRegistry registry) {
    return builder(new ClusterConfig(), registry);
  }

  /**
   * Returns a new Atomix builder.
   *
   * @param config the Atomix configuration
   * @return a new Atomix builder
   */
  public static AtomixClusterBuilder builder(
      final ClusterConfig config, final MeterRegistry registry) {
    return new AtomixClusterBuilder(config, registry);
  }

  /**
   * Returns the cluster messaging service.
   *
   * <p>The messaging service is used for direct point-to-point messaging between nodes by {@link
   * Address}. This is a low-level cluster communication API. For higher level messaging, use the
   * {@link #getCommunicationService() communication service} or {@link #getEventService() event
   * service}.
   *
   * @return the cluster messaging service
   */
  @Override
  public MessagingService getMessagingService() {
    return messagingService;
  }

  /**
   * Returns the cluster unicast service.
   *
   * <p>The unicast service supports unreliable uni-directional messaging via UDP. This is a
   * low-level cluster communication API. For higher level messaging, use the {@link
   * #getCommunicationService() communication service} or {@link #getEventService() event service}.
   *
   * @return the cluster unicast service
   */
  @Override
  public UnicastService getUnicastService() {
    return unicastService;
  }

  /**
   * Returns the cluster membership service.
   *
   * <p>The membership service manages cluster membership information and failure detection.
   *
   * @return the cluster membership service
   */
  public ClusterMembershipService getMembershipService() {
    return membershipService;
  }

  /**
   * Returns the cluster communication service.
   *
   * <p>The cluster communication service is used for high-level unicast, multicast, and
   * request-reply messaging.
   *
   * @return the cluster communication service
   */
  public ClusterCommunicationService getCommunicationService() {
    return communicationService;
  }

  /**
   * Returns the cluster event service.
   *
   * <p>The cluster event service is used for high-level publish-subscribe messaging.
   *
   * @return the cluster event service
   */
  public ClusterEventService getEventService() {
    return eventService;
  }

  @Override
  public synchronized CompletableFuture<Void> start() {
    if (closeFuture != null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "Cluster instance is " + (closeFuture.isDone() ? "shutdown" : "shutting down")));
    }

    if (openFuture != null) {
      return openFuture;
    }

    openFuture = startServices().thenComposeAsync(v -> completeStartup(), threadContext);

    return openFuture;
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public synchronized CompletableFuture<Void> stop() {
    if (closeFuture != null) {
      return closeFuture;
    }

    closeFuture = stopServices().thenComposeAsync(v -> completeShutdown(), threadContext);
    return closeFuture;
  }

  protected CompletableFuture<Void> startServices() {
    return messagingService
        .start()
        .thenComposeAsync(v -> unicastService.start(), threadContext)
        .thenComposeAsync(v -> membershipService.start(), threadContext)
        .thenComposeAsync(v -> communicationService.start(), threadContext)
        .thenComposeAsync(v -> eventService.start(), threadContext)
        .thenApply(v -> null);
  }

  protected CompletableFuture<Void> completeStartup() {
    started.set(true);
    return CompletableFuture.completedFuture(null);
  }

  private Void logServiceStopError(final String serviceName, final Throwable throwable) {
    LOGGER.error("Failed to stop service {}", serviceName, throwable);
    return null;
  }

  protected CompletableFuture<Void> stopServices() {
    return communicationService
        .stop()
        .exceptionally(e -> logServiceStopError("communicationService", e))
        .thenComposeAsync(v -> eventService.stop(), threadContext)
        .exceptionally(e -> logServiceStopError("eventService", e))
        .thenComposeAsync(v -> membershipService.stop(), threadContext)
        .exceptionally(e -> logServiceStopError("membershipService", e))
        .thenComposeAsync(v -> unicastService.stop(), threadContext)
        .exceptionally(e -> logServiceStopError("unicastService", e))
        .thenComposeAsync(v -> messagingService.stop(), threadContext)
        .exceptionally(e -> logServiceStopError("messagingService", e));
  }

  protected CompletableFuture<Void> completeShutdown() {
    threadContext.close();
    started.set(false);
    LOGGER.info("Stopped");
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public String toString() {
    return toStringHelper(this).toString();
  }

  /** Builds a default messaging service. */
  protected static ManagedMessagingService buildMessagingService(
      final ClusterConfig config, final String actorSchedulerName, final MeterRegistry registry) {
    return new NettyMessagingService(
        config.getClusterId(),
        config.getNodeConfig().getAddress(),
        config.getMessagingConfig(),
        actorSchedulerName,
        registry);
  }

  /** Builds a default unicast service. */
  protected static ManagedUnicastService buildUnicastService(
      final ClusterConfig config, final String actorSchedulerName, final MeterRegistry registry) {
    return new NettyUnicastService(
        config.getClusterId(),
        config.getNodeConfig().getAddress(),
        config.getMessagingConfig(),
        actorSchedulerName,
        registry);
  }

  /** Builds a member location provider. */
  @SuppressWarnings("unchecked")
  protected static NodeDiscoveryProvider buildLocationProvider(final ClusterConfig config) {
    final NodeDiscoveryConfig discoveryProviderConfig = config.getDiscoveryConfig();
    if (discoveryProviderConfig != null) {
      return discoveryProviderConfig.getType().newProvider(discoveryProviderConfig);
    }

    return new BootstrapDiscoveryProvider(Collections.emptyList());
  }

  /** Builds the group membership protocol. */
  @SuppressWarnings("unchecked")
  protected static GroupMembershipProtocol buildMembershipProtocol(
      final ClusterConfig config, final String actorSchedulerName, final MeterRegistry registry) {
    return config
        .getProtocolConfig()
        .getType()
        .newProtocol(config.getProtocolConfig(), actorSchedulerName, registry);
  }

  /** Builds a cluster service. */
  protected static ManagedClusterMembershipService buildClusterMembershipService(
      final ClusterConfig config,
      final BootstrapService bootstrapService,
      final NodeDiscoveryProvider discoveryProvider,
      final GroupMembershipProtocol membershipProtocol,
      final Version version) {
    // If the local node has not be configured, create a default node.
    final Member localMember =
        Member.builder()
            .withId(config.getNodeConfig().getId())
            .withAddress(config.getNodeConfig().getAddress())
            .withHostId(config.getNodeConfig().getHostId())
            .withRackId(config.getNodeConfig().getRackId())
            .withZoneId(config.getNodeConfig().getZoneId())
            .withProperties(config.getNodeConfig().getProperties())
            .build();
    return new DefaultClusterMembershipService(
        localMember,
        version,
        new DefaultNodeDiscoveryService(bootstrapService, localMember, discoveryProvider),
        bootstrapService,
        membershipProtocol);
  }

  /** Builds a cluster messaging service. */
  protected static ManagedClusterCommunicationService buildClusterMessagingService(
      final ClusterMembershipService membershipService,
      final MessagingService messagingService,
      final UnicastService unicastService) {
    return new DefaultClusterCommunicationService(
        membershipService, messagingService, unicastService);
  }

  /** Builds a cluster event service. */
  protected static ManagedClusterEventService buildClusterEventService(
      final ClusterMembershipService membershipService, final MessagingService messagingService) {
    return new DefaultClusterEventService(membershipService, messagingService);
  }
}
