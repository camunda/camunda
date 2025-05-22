/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

import java.util.Optional;

/**
 * Listener to changes in "graph" of monitored components. Primary use case is to export the graph
 * to a human-readable format (such as grafana).
 */
public interface ComponentTreeListener extends AutoCloseable {

  /**
   * Register a node in the graph. If the node has a parent, its parent-child relationship must be
   * registered before registering the node. Using the method {@link
   * ComponentTreeListener#registerNode(HealthMonitorable, Optional)} is preferred.
   *
   * @param component to be registered
   */
  void registerNode(HealthMonitorable component);

  /**
   * Register a new node, together with its relationship with its parent.
   *
   * @param component to register
   * @param parent parent of the component, will be registered before registering the node
   */
  default void registerNode(final HealthMonitorable component, final Optional<String> parent) {
    parent.ifPresent(p -> registerRelationship(component.componentName(), p));
    registerNode(component);
  }

  default void registerNode(final HealthMonitorable component, final HealthMonitorable parent) {
    registerNode(component, Optional.ofNullable(parent.componentName()));
  }

  void unregisterNode(HealthMonitorable component);

  /**
   * Register a relationship between a child and its parent. It must be called before registering
   * the child
   */
  void registerRelationship(String child, String parent);

  /** Unregister a relationship between a child and its parent */
  void unregisterRelationship(String child, String parent);

  default void unregisterRelationship(
      final HealthMonitorable child, final HealthMonitorable parent) {
    unregisterRelationship(child.componentName(), parent.componentName());
  }

  static ComponentTreeListener noop() {
    return new ComponentTreeListener() {
      @Override
      public void registerNode(final HealthMonitorable component) {}

      @Override
      public void unregisterNode(final HealthMonitorable component) {}

      @Override
      public void registerRelationship(final String child, final String parent) {}

      @Override
      public void unregisterRelationship(final String child, final String parent) {}

      @Override
      public void close() throws Exception {}
    };
  }
}
