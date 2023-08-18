/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;

public interface Zeebe<T extends Zeebe<T>> {

  /** Returns this node's unique cluster ID */
  MemberId nodeId();

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
  default String address(final ZeebePort port) {
    return address(mappedPort(port));
  }

  /**
   * Returns the address to access the monitoring API of this node from outside the container
   * network of this node.
   *
   * @return the external monitoring address
   */
  default String monitoringAddress() {
    return address(ZeebePort.MONITORING);
  }

  /**
   * Returns the hostname of this node, such that it is visible to hosts from the outside of the
   * Docker network.
   *
   * @return the hostname of this node
   */
  String host();

  /** Starts the node in a blocking fashion. */
  void start();

  /** Attempts to stop the container gracefully in a blocking fashion. */
  void shutdown();

  HealthActuator healthActuator();

  /** Probes for the given health probe; throws an exception on failure. */
  default void probe(final ZeebeHealthProbe probe) {
    switch (probe) {
      case LIVE -> healthActuator().live();
      case READY -> healthActuator().ready();
      case STARTUP -> healthActuator().startup();
      default -> throw new IllegalStateException("Unexpected value: " + probe);
    }
  }

  /** Returns whether the underlying application is started yet; does not include any probes */
  boolean isStarted();

  /** Returns the actual port for the given logical port. */
  default int mappedPort(final ZeebePort port) {
    return port.port();
  }
}
