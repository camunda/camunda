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
package io.atomix.cluster.discovery;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.atomix.utils.concurrent.Threads.namedThreads;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.Node;
import io.atomix.cluster.NodeId;
import io.atomix.utils.event.AbstractListenerManager;
import java.time.Duration;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cluster membership provider that uses DNS SRV lookups. */
public class DnsDiscoveryProvider
    extends AbstractListenerManager<NodeDiscoveryEvent, NodeDiscoveryEventListener>
    implements NodeDiscoveryProvider {

  public static final Type TYPE = new Type();
  private static final Logger LOGGER = LoggerFactory.getLogger(DnsDiscoveryProvider.class);
  private static final String[] ATTRIBUTES = new String[] {"SRV"};
  private static final String ATTRIBUTE_ID = "srv";
  private final ScheduledExecutorService resolverScheduler =
      Executors.newSingleThreadScheduledExecutor(
          namedThreads("atomix-cluster-dns-resolver", LOGGER));
  private final String service;
  private final Duration resolutionInterval;
  private final DnsDiscoveryConfig config;
  private final Map<NodeId, Node> nodes = Maps.newConcurrentMap();

  public DnsDiscoveryProvider(final String service) {
    this(new DnsDiscoveryConfig().setService(service));
  }

  DnsDiscoveryProvider(final DnsDiscoveryConfig config) {
    this.config = checkNotNull(config, "config cannot be null");
    this.service = checkNotNull(config.getService(), "service cannot be null");
    this.resolutionInterval =
        checkNotNull(config.getResolutionInterval(), "resolutionInterval cannot be null");
  }

  /**
   * Creates a new DNS provider builder.
   *
   * @return a new DNS provider builder
   */
  public static DnsDiscoveryBuilder builder() {
    return new DnsDiscoveryBuilder();
  }

  @Override
  public DnsDiscoveryConfig config() {
    return config;
  }

  @Override
  public Set<Node> getNodes() {
    return ImmutableSet.copyOf(nodes.values());
  }

  @Override
  public CompletableFuture<Void> join(final BootstrapService bootstrap, final Node localNode) {
    LOGGER.info("Joined");
    resolverScheduler.scheduleAtFixedRate(
        this::resolveNodes, 0, resolutionInterval.toMillis(), TimeUnit.MILLISECONDS);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> leave(final Node localNode) {
    LOGGER.info("Left");
    resolverScheduler.shutdownNow();
    return CompletableFuture.completedFuture(null);
  }

  private void resolveNodes() {
    final Hashtable<String, String> env = new Hashtable<>();
    env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
    env.put("java.naming.provider.url", "dns:");

    try {
      final DirContext context = new InitialDirContext(env);
      final NamingEnumeration<?> resolved =
          context.getAttributes(service, ATTRIBUTES).get(ATTRIBUTE_ID).getAll();

      final Set<NodeId> currentNodeIds = ImmutableSet.copyOf(nodes.keySet());
      final Set<NodeId> newNodeIds = Sets.newHashSet();
      while (resolved.hasMore()) {
        final String record = (String) resolved.next();
        final String[] items = record.split(" ", -1);
        final String host = items[3].trim();
        final String port = items[2].trim();
        final String id = Splitter.on('.').splitToList(host).get(0);

        final Node node =
            Node.builder().withId(id).withHost(host).withPort(Integer.parseInt(port)).build();

        if (nodes.putIfAbsent(node.id(), node) == null) {
          newNodeIds.add(node.id());
          LOGGER.info("Node joined: {}", node);
          post(new NodeDiscoveryEvent(NodeDiscoveryEvent.Type.JOIN, node));
        }
      }

      for (final NodeId nodeId : currentNodeIds) {
        if (!newNodeIds.contains(nodeId)) {
          final Node node = nodes.remove(nodeId);
          if (node != null) {
            LOGGER.info("Node left: {}", node);
            post(new NodeDiscoveryEvent(NodeDiscoveryEvent.Type.LEAVE, node));
          }
        }
      }
    } catch (final NamingException e) {
      LOGGER.debug("Failed to resolve DNS SRV record {}", service, e);
    }
  }

  /** DNS node discovery provider type. */
  public static class Type implements NodeDiscoveryProvider.Type<DnsDiscoveryConfig> {
    private static final String NAME = "dns";

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public DnsDiscoveryConfig newConfig() {
      return new DnsDiscoveryConfig();
    }

    @Override
    public NodeDiscoveryProvider newProvider(final DnsDiscoveryConfig config) {
      return new DnsDiscoveryProvider(config);
    }
  }
}
