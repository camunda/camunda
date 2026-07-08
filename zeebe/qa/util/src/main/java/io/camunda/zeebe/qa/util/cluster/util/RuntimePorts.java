/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster.util;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.bootstrap.BrokerContext;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Resolves the ports an application is actually bound to at runtime. Test applications are
 * configured with ephemeral ports (0) so that the OS assigns free ports on bind; the actual ports
 * are only known once the server sockets are bound, which happens asynchronously during startup.
 *
 * <p>All resolvers are non-blocking and return an empty result while the port is not bound yet (or
 * the application failed before binding it); callers are expected to poll. They also work while the
 * application is still starting, e.g. to resolve a cluster seed's port while its startup blocks on
 * cluster formation.
 */
public final class RuntimePorts {

  private RuntimePorts() {}

  /** Resolves the cluster (internal API) port from the running Atomix cluster. */
  public static OptionalInt clusterPort(final TestSpringApplication<?> app) {
    return resolve(
        () -> {
          final var atomix = app.startedSingleton(AtomixCluster.class);
          if (atomix == null || !atomix.isRunning()) {
            return OptionalInt.empty();
          }
          return nonEphemeral(atomix.getMessagingService().address().port());
        });
  }

  /** Resolves the command API port from the running broker's API messaging service. */
  public static OptionalInt commandApiPort(final TestSpringApplication<?> app) {
    return resolve(
        () -> {
          final var brokerContext = brokerContext(app);
          if (brokerContext == null || brokerContext.getApiMessagingService() == null) {
            return OptionalInt.empty();
          }
          return nonEphemeral(brokerContext.getApiMessagingService().address().port());
        });
  }

  /** Resolves the gRPC port of a broker's embedded gateway. */
  public static OptionalInt embeddedGatewayPort(final TestSpringApplication<?> app) {
    return resolve(
        () -> {
          final var brokerContext = brokerContext(app);
          if (brokerContext == null || brokerContext.getEmbeddedGatewayService() == null) {
            return OptionalInt.empty();
          }
          return nonEphemeral(brokerContext.getEmbeddedGatewayService().get().getServerPort());
        });
  }

  /** Resolves the gRPC port of a standalone gateway. */
  public static OptionalInt standaloneGatewayPort(final TestSpringApplication<?> app) {
    return resolve(
        () -> {
          final var gateway = app.startedSingleton(Gateway.class);
          if (gateway == null) {
            return OptionalInt.empty();
          }
          return nonEphemeral(gateway.getServerPort());
        });
  }

  private static BrokerContext brokerContext(final TestSpringApplication<?> app) {
    final var broker = app.startedSingleton(Broker.class);
    return broker == null ? null : broker.getBrokerContext();
  }

  private static OptionalInt nonEphemeral(final int port) {
    return port == 0 ? OptionalInt.empty() : OptionalInt.of(port);
  }

  private static OptionalInt resolve(final Supplier<OptionalInt> resolver) {
    try {
      return resolver.get();
    } catch (final Exception e) {
      // beans may be missing or not fully started yet; treat as not resolvable
      return OptionalInt.empty();
    }
  }
}
