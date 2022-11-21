/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.testcontainers;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
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
  private static final AtomicInteger PORT_GENERATOR = new AtomicInteger(1024);

  // concurrent to allow static usage along with static containers and parallel tests
  private final ConcurrentMap<String, ContainerProxy> proxies = new ConcurrentHashMap<>();
  private final ToxiproxyContainer toxiproxy;
  private ToxiproxyClient lazyClient;

  public ProxyRegistry(final ToxiproxyContainer toxiproxy) {
    this.toxiproxy = toxiproxy;
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

  private ContainerProxy createProxy(final String upstream) {
    final var proxyPort = PORT_GENERATOR.getAndIncrement();

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
