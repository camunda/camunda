/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;

public interface TestApplication<T extends TestApplication<T>> extends AutoCloseable {

  /** Starts the node in a blocking fashion. */
  T start();

  /** Attempts to stop the container gracefully in a blocking fashion. */
  T stop();

  /** Returns whether the underlying application is started yet; does not include any probes */
  boolean isStarted();

  @Override
  default void close() {
    //noinspection resource
    stop();
  }

  /** Convenience method to return the appropriate concrete type */
  T self();

  /**
   * When the underlying application is started, all beans of the given type will resolve to the
   * given value. The qualifier is useful for cases where more than one beans of the same type are
   * defined with different qualifiers.
   *
   * @param qualifier the bean name/qualifier
   * @param bean the object to inject as the bean value
   * @param type the type to be resolved/autowired
   * @return itself for chaining
   * @param <V> the bean type
   */
  @SuppressWarnings("UnusedReturnValue")
  <V> T withBean(final String qualifier, final V bean, final Class<V> type);

  /** Returns this node's unique cluster ID */
  MemberId nodeId();

  /**
   * Returns the hostname of this node, such that it is visible to hosts from the outside of the
   * Docker network.
   *
   * @return the hostname of this node
   */
  default String host() {
    return "localhost";
  }

  /** Returns the actual port for the given logical port. */
  default int mappedPort(final TestZeebePort port) {
    return port.port();
  }

  /**
   * Returns the address of this node for the given port.
   *
   * @param port the target port
   * @return externally accessible address for {@code port}
   */
  default String address(final int port) {
    return host() + ":" + port;
  }

  /**
   * Returns the address of this node for the given port.
   *
   * @param port the target port
   * @return externally accessible address for {@code port}
   */
  default String address(final TestZeebePort port) {
    return address(mappedPort(port));
  }

  /**
   * Returns the address to access the monitoring API of this node from outside the container
   * network of this node.
   *
   * @return the external monitoring address
   */
  default String monitoringAddress() {
    return address(TestZeebePort.MONITORING);
  }
}
