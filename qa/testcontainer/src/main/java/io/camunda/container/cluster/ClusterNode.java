/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container.cluster;

import io.camunda.configuration.Camunda;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;
import org.testcontainers.lifecycle.Startable;

public interface ClusterNode<T extends GenericContainer<T> & ClusterNode<T>>
    extends Container<T>, WaitStrategyTarget, Startable {
  /**
   * Returns an address accessible from within the container's network for the given port.
   *
   * @param port the target port
   * @return internally accessible address for {@code port}
   */
  default String getInternalAddress(final int port) {
    return getInternalHost() + ":" + port;
  }

  /**
   * Returns an address accessible outside the container's network for the given port.
   *
   * @param port the target port
   * @return externally accessible address for {@code port}
   */
  default String getExternalAddress(final int port) {
    return getExternalHost() + ":" + getMappedPort(port);
  }

  /**
   * Returns the address that nodes should use to talk to each other within the docker network. When
   * starting a cluster of containers, this is what you want to use for the initial contact points
   * so the nodes can find each other.
   *
   * @return the internal cluster address
   */
  default String getInternalClusterAddress() {
    return getInternalAddress(CamundaPort.INTERNAL.getPort());
  }

  /**
   * Returns the address that a Zeebe node outside the docker network can use to talk to this node.
   *
   * @return the external cluster address
   */
  default String getExternalClusterAddress() {
    return getExternalAddress(CamundaPort.INTERNAL.getPort());
  }

  /**
   * Returns the address to access the monitoring API of this node from within the same container
   * network as this node's.
   *
   * @return the internal monitoring address
   */
  default String getInternalMonitoringAddress() {
    return getInternalAddress(CamundaPort.MONITORING.getPort());
  }

  /**
   * Returns the address to access the monitoring API of this node from outside the container
   * network of this node.
   *
   * @return the external monitoring address
   */
  default String getExternalMonitoringAddress() {
    return getExternalAddress(CamundaPort.MONITORING.getPort());
  }

  /**
   * Returns the hostname of this node, such that it is visible to hosts from the outside of the
   * Docker network.
   *
   * @return the hostname of this node
   */
  default String getExternalHost() {
    return self().getHost();
  }

  /**
   * Returns a hostname which is accessible from a host that is within the same docker network as
   * this node. It will attempt to return the last added network alias it finds, and if there is
   * none, will return the container name. The network alias is preferable as it typically conveys
   * more meaning than container name, which is often randomly generated.
   *
   * @return the hostname of this node as visible from a host within the same docker network
   */
  default String getInternalHost() {
    final GenericContainer<?> container = self();
    final List<String> aliases = container.getNetworkAliases();
    if (aliases.isEmpty()) {
      return container.getContainerInfo().getName();
    }

    return aliases.get(aliases.size() - 1);
  }

  /**
   * Attempts to stop the container gracefully. If it times out, the container is abruptly killed.
   * The use case here is that {@link GenericContainer#stop()} actually kills and removes the
   * container, preventing us from:
   *
   * <ul>
   *   <li>shutting it down gracefully
   *   <li>restarting it
   * </ul>
   *
   * <p>There is an issue opened for this <a
   * href="https://github.com/testcontainers/testcontainers-java/issues/1000">here</a>
   *
   * @param timeout must be greater than 1 second
   */
  default void shutdownGracefully(final Duration timeout) {
    final String containerId = getContainerId();
    if (containerId == null) {
      return;
    }

    getDockerClient().stopContainerCmd(containerId).withTimeout((int) timeout.getSeconds()).exec();
  }

  /**
   * Returns whether the container was started or not yet by checking if it was assigned an ID.
   *
   * @return true if the container is already started, false otherwise
   */
  default boolean isStarted() {
    return getContainerId() != null;
  }

  /**
   * A convenience method to allow adding exposed ports in a chainable way.
   *
   * <p>Currently, you can define exposed ports in two ways:
   *
   * <ul>
   *   <li>{@link GenericContainer#withExposedPorts(Integer...)}
   *   <li>{@link GenericContainer#addExposedPorts(int...)}
   * </ul>
   *
   * Unfortunately, the first option will overwrite any previously exposed port, which leaves us
   * only with the second option. However, this one does not return the container for chaining, thus
   * breaking the fluent builder API.
   *
   * @param port the port to expose
   * @return itself for chaining
   */
  default T withAdditionalExposedPort(final int port) {
    self().addExposedPorts(port);
    return self();
  }

  T withUnifiedConfig(final Consumer<Camunda> configurer);

  T withProperty(final String name, final Object value);

  Camunda getConfiguration();

  Map<String, Object> getAdditionalConfigs();
}
