/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.testcontainers;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.testcontainers.containers.ToxiproxyContainer;

/**
 * A utility registry to keep track of proxies that were created for a given {@link
 * ToxiproxyContainer}. Proxies are identified by their upstream target, and cannot be removed from
 * the registry for now.
 *
 * <p>NOTE: before proxies can be created, the associated container must be started!
 *
 * <p>NOTE: this class should be extended based on usage, i.e. as needed.
 */
public final class ProxyRegistry {

  // used to generate unique listen ports on the Toxiproxy container; each proxy will use a single
  // port, which must be unique to avoid collisions
  private static final int MIN_EXPOSED_PORT = 10_000;
  private static final int MAX_EXPOSED_PORT = MIN_EXPOSED_PORT + 32;

  // the name under which the container reaches ports bound on the host running the tests
  private static final String HOST_ALIAS = "host.testcontainers.internal";

  private final ToxiproxyContainer toxiproxy;

  // everything below describes the container run identified by containerId, and is guarded by
  // this; see resetIfContainerRestarted
  private final Map<String, ContainerProxy> proxies = new HashMap<>();
  private int nextPort = MIN_EXPOSED_PORT;
  private ToxiproxyClient client;
  private String containerId;

  public ProxyRegistry(final ToxiproxyContainer toxiproxy) {
    this.toxiproxy = toxiproxy;
  }

  /**
   * Exposes the ports proxies listen on, and lets the container reach ports bound on the host, as
   * {@link #getOrCreateHostProxy(int)} requires.
   *
   * <p>The host is reached over Docker's {@code host-gateway}, i.e. directly through the bridge
   * network, and deliberately not via {@link ToxiproxyContainer#withAccessToHost(boolean)}. That
   * would send every proxied connection through an SSH tunnel whose client runs inside the test
   * JVM; a cluster's membership and Raft traffic saturates it under CI load, and the sub-second
   * membership timeouts then fail for minutes at a time.
   */
  public static ToxiproxyContainer addExposedPorts(final ToxiproxyContainer container) {
    container.addExposedPorts(IntStream.range(MIN_EXPOSED_PORT, MAX_EXPOSED_PORT).toArray());
    container.withExtraHost(HOST_ALIAS, "host-gateway");
    return container;
  }

  /**
   * Returns the proxy associated with the given upstream, or creates a new instance.
   *
   * @param upstream the upstream endpoint that the proxy points to
   * @return a {@link ContainerProxy} which can be used to access the proxy
   */
  public synchronized ContainerProxy getOrCreateProxy(final String upstream) {
    resetIfContainerRestarted();
    return proxies.computeIfAbsent(upstream, this::createProxy);
  }

  /**
   * Returns the proxy associated with the given port on the local host, or creates a new instance.
   * The port must be bound on an interface the Docker bridge network can reach, e.g. {@code
   * 0.0.0.0}, and the container must have been prepared with {@link
   * #addExposedPorts(ToxiproxyContainer)}.
   *
   * @param port the upstream port that the proxy points to
   * @return a {@link ContainerProxy} which can be used to access the proxy
   */
  public ContainerProxy getOrCreateHostProxy(final int port) {
    return getOrCreateProxy(HOST_ALIAS + ":" + port);
  }

  private ContainerProxy createProxy(final String upstream) {
    if (nextPort >= MAX_EXPOSED_PORT) {
      throw new IllegalStateException(
          "Cannot proxy more than %d ports with a single container"
              .formatted(MAX_EXPOSED_PORT - MIN_EXPOSED_PORT));
    }

    final var proxyPort = nextPort++;
    try {
      final var proxy =
          Objects.requireNonNull(client)
              .createProxy(upstream, "0.0.0.0:%d".formatted(proxyPort), upstream);
      return new ContainerProxy(proxy, proxyPort);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Proxies live in the container, so a container that was stopped and started again is empty and
   * publishes new host ports. That happens when a test class keeps the container and this registry
   * in static fields and is rerun after a failure: the container is recreated, the registry is not.
   * Forget everything recorded for the previous run, so that the client targets the live control
   * port and each proxy is created anew instead of being looked up in a container that never had
   * it.
   */
  private void resetIfContainerRestarted() {
    final var currentId = toxiproxy.getContainerId();
    if (Objects.equals(currentId, containerId)) {
      return;
    }

    containerId = currentId;
    client = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
    proxies.clear();
    nextPort = MIN_EXPOSED_PORT;
  }

  /**
   * Wrapper type which keeps track of each proxy and their associated unmapped port. If you need to
   * get the port as accessible from outside the container network, you can use the associated
   * {@link ToxiproxyContainer#getMappedPort(int)} and pass the {@link #internalPort()} here.
   *
   * @param proxy the Toxiproxy proxy instance
   * @param internalPort the unmapped port associated with this proxy on the container
   */
  public record ContainerProxy(Proxy proxy, int internalPort) {}
}
