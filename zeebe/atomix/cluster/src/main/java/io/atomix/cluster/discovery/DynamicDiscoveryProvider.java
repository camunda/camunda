/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.discovery;

import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;
import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.Node;
import io.atomix.cluster.NodeConfig;
import io.atomix.cluster.NodeId;
import io.atomix.utils.event.AbstractListenerManager;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.util.VisibleForTesting;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dynamic cluster node discovery provider.
 *
 * <p>This provider resolves DNS addresses to discover nodes in the cluster. It supports:
 *
 * <ul>
 *   <li>Host names that resolve to multiple IP addresses
 *   <li>IPv4 addresses
 *   <li>Addresses with or without explicit ports (defaults to MessagingConfig port)
 *   <li>Periodic DNS resolution refresh to detect changes
 * </ul>
 *
 * <p>The provider periodically refreshes DNS resolutions and fires {@link
 * NodeDiscoveryEvent.Type#JOIN} events when new nodes are discovered and {@link
 * NodeDiscoveryEvent.Type#LEAVE} events when nodes are no longer resolved.
 */
public final class DynamicDiscoveryProvider
    extends AbstractListenerManager<NodeDiscoveryEvent, NodeDiscoveryEventListener>
    implements NodeDiscoveryProvider {

  public static final Type TYPE = new Type();
  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicDiscoveryProvider.class);

  private final DynamicDiscoveryConfig config;
  private final Set<Node> discoveredNodes = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            final Thread thread = new Thread(runnable, "dynamic-discovery");
            thread.setDaemon(true);
            return thread;
          });

  private final int defaultPort;
  private ScheduledFuture<?> refreshTask;
  private final Function<String, List<InetAddress>> hostnameResolver;

  /**
   * Creates a new DNS discovery provider with the given configuration.
   *
   * @param config the DNS discovery configuration
   */
  public DynamicDiscoveryProvider(final DynamicDiscoveryConfig config) {
    this(config, DynamicDiscoveryProvider::resolveAddress);
  }

  @VisibleForTesting
  DynamicDiscoveryProvider(
      final DynamicDiscoveryConfig config,
      final Function<String, List<InetAddress>> hostnameResolver) {
    this.config = Objects.requireNonNull(config, "config cannot be null");
    defaultPort = config.getDefaultPort();
    if (config.getRefreshInterval() == null
        || config.getRefreshInterval().isZero()
        || config.getRefreshInterval().isNegative()) {
      throw new IllegalArgumentException(
          "Refresh interval must be a positive duration, but given: "
              + config.getRefreshInterval());
    }
    this.hostnameResolver = hostnameResolver;
  }

  @Override
  public DynamicDiscoveryConfig config() {
    return config;
  }

  @Override
  public Set<Node> getNodes() {
    return Set.copyOf(discoveredNodes);
  }

  @Override
  public CompletableFuture<Void> join(final BootstrapService bootstrap, final Node localNode) {
    if (started.compareAndSet(false, true)) {
      scheduler.submit(
          () -> {
            scheduleRefresh();

            LOGGER.debug(
                "Dynamic discovery started with {} nodes discovered", discoveredNodes.size());
          });
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> leave(final Node localNode) {
    if (started.compareAndSet(true, false)) {
      LOGGER.info("Stopping Dynamic discovery");

      // Cancel refresh task
      if (refreshTask != null) {
        refreshTask.cancel(false);
        refreshTask = null;
      }

      // Clear discovered nodes
      discoveredNodes.clear();

      // Shutdown scheduler
      scheduler.shutdownNow();

      LOGGER.info("Dynamic discovery stopped");
    }
    return CompletableFuture.completedFuture(null);
  }

  private void scheduleRefresh() {
    refreshNodes();
    final Duration refreshInterval = config.getRefreshInterval();
    LOGGER.trace("Scheduling node refresh in {}", refreshInterval);
    refreshTask =
        scheduler.schedule(
            this::scheduleRefresh, refreshInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  /** Refreshes the list of discovered nodes by re-resolving DNS addresses. */
  private void refreshNodes() {
    try {
      final Set<Node> newNodes = discoverNodes();
      final Set<Node> currentNodes = Set.copyOf(discoveredNodes);

      // Determine newly joined nodes
      final Set<Node> joinedNodes = Sets.difference(newNodes, currentNodes);
      for (final Node node : joinedNodes) {
        discoveredNodes.add(node);
        LOGGER.debug("Node joined: {}", node);
        post(new NodeDiscoveryEvent(NodeDiscoveryEvent.Type.JOIN, node));
      }

      // Determine nodes that left
      final Set<Node> leftNodes = Sets.difference(currentNodes, newNodes);
      for (final Node node : leftNodes) {
        discoveredNodes.remove(node);
        LOGGER.debug("Node left: {}", node);
        post(new NodeDiscoveryEvent(NodeDiscoveryEvent.Type.LEAVE, node));
      }

      if (!joinedNodes.isEmpty() || !leftNodes.isEmpty()) {
        LOGGER.info(
            "{} nodes joined, {} nodes left, {} total nodes",
            joinedNodes.size(),
            leftNodes.size(),
            discoveredNodes.size());
      }
    } catch (final Exception e) {
      LOGGER.error("Error refreshing discovery nodes", e);
    }
  }

  /**
   * Resolves all configured DNS addresses to a set of nodes.
   *
   * @return the set of resolved nodes
   */
  private Set<Node> discoverNodes() {
    final Set<Node> nodes = ConcurrentHashMap.newKeySet();

    for (final String address : config.getAddresses()) {
      try {
        nodes.addAll(discoverAddress(address));
      } catch (final Exception e) {
        LOGGER.warn("Failed to resolve address: {}", address, e);
      }
    }

    return nodes;
  }

  /**
   * Resolves a single DNS address to a set of nodes. If the hostname resolves to multiple IP
   * addresses, creates a node for each one.
   *
   * @param address the address in format "host:port" or "host"
   * @return the set of resolved nodes
   */
  private Set<Node> discoverAddress(final String address) {
    final Set<Node> nodes = ConcurrentHashMap.newKeySet();

    try {
      // Parse host and port
      final HostAndPort hostAndPort = HostAndPort.fromString(address).withDefaultPort(defaultPort);
      final String host = hostAndPort.getHost();
      final int port = hostAndPort.getPort();

      // Resolve DNS to all IP addresses
      final List<InetAddress> addresses = hostnameResolver.apply(host);

      for (final InetAddress inetAddress : addresses) {
        final String ipAddress = inetAddress.getHostAddress();
        final Address nodeAddress = new Address(ipAddress, port, inetAddress);

        // Create node ID from the resolved address
        final NodeId nodeId = NodeId.from(ipAddress + ":" + port);
        final NodeConfig nodeConfig = new NodeConfig().setId(nodeId).setAddress(nodeAddress);
        final Node node = new Node(nodeConfig);

        nodes.add(node);
        LOGGER.debug("Resolved {} to {} ({}:{})", address, inetAddress, ipAddress, port);
      }

      if (nodes.isEmpty()) {
        LOGGER.warn("No addresses resolved for: {}", address);
      }
    } catch (final Exception e) {
      LOGGER.debug("Error resolving address: {}", address, e);
    }

    return nodes;
  }

  private static List<InetAddress> resolveAddress(final String host) {
    try {
      return Arrays.asList(InetAddress.getAllByName(host));
    } catch (final UnknownHostException e) {
      LOGGER.warn("Failed to resolve DNS for address: {}", host, e);
      return List.of();
    }
  }

  public static class Type implements NodeDiscoveryProvider.Type<DynamicDiscoveryConfig> {
    private static final String NAME = "dynamic";

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public NodeDiscoveryProvider newProvider(final DynamicDiscoveryConfig config) {
      return new DynamicDiscoveryProvider(config);
    }
  }
}
