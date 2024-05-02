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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.testcontainers.Testcontainers;
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
  // port, which must be unique to avoid collisions. Starts at 1024, since ports below are reserved
  // by the kernel
  private static final int MIN_EXPOSED_PORT = 10_000;
  private static final int MAX_EXPOSED_PORT = MIN_EXPOSED_PORT + 32;
  private static final AtomicInteger PORT_GENERATOR = new AtomicInteger(MIN_EXPOSED_PORT);

  // concurrent to allow static usage along with static containers and parallel tests
  private final ConcurrentMap<String, ContainerProxy> proxies = new ConcurrentHashMap<>();
  private final ToxiproxyContainer toxiproxy;
  private ToxiproxyClient lazyClient;

  public ProxyRegistry(final ToxiproxyContainer toxiproxy) {
    this.toxiproxy = toxiproxy;
  }

  public static ToxiproxyContainer addExposedPorts(final ToxiproxyContainer container) {
    container.addExposedPorts(IntStream.range(MIN_EXPOSED_PORT, MAX_EXPOSED_PORT).toArray());
    container.withAccessToHost(true);
    return container;
  }

  /**
   * Returns the proxy associated with the given upstream, or creates a new instance.
   *
   * @param upstream the upstream endpoint that the proxy points to
   * @return a {@link ContainerProxy} which can be used to access the proxy
   */
  public ContainerProxy getOrCreateProxy(final String upstream) {
    return proxies.computeIfAbsent(upstream, this::createProxy);
  }

  /**
   * Returns the proxy associated with the given port on the local host, or creates a new instance.
   *
   * @param port the upstream port that the proxy points to
   * @return a {@link ContainerProxy} which can be used to access the proxy
   */
  public ContainerProxy getOrCreateHostProxy(final int port) {
    final var upstream = "host.testcontainers.internal:" + port;
    Testcontainers.exposeHostPorts(port);
    return getOrCreateProxy(upstream);
  }

  private ContainerProxy createProxy(final String upstream) {
    final var proxyPort = PORT_GENERATOR.getAndIncrement();

    if (proxyPort >= MAX_EXPOSED_PORT) {
      throw new IllegalStateException(
          "Cannot proxy more than %d ports with a single container"
              .formatted(MAX_EXPOSED_PORT - MIN_EXPOSED_PORT));
    }

    try {
      final var proxy =
          getClient().createProxy(upstream, "0.0.0.0:%d".formatted(proxyPort), upstream);
      return new ContainerProxy(proxy, proxyPort);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private synchronized ToxiproxyClient getClient() {
    if (lazyClient == null) {
      lazyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
    }

    return lazyClient;
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
