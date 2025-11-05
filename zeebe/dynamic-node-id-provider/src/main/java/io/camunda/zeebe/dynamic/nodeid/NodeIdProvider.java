/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import java.util.concurrent.CompletableFuture;

public interface NodeIdProvider {
  NodeInstance currentNodeInstance();

  // FIXME rename
  CompletableFuture<Boolean> isValid();

  /**
   * NodeIdProvider to be used when the nodeId is static, i.e. it's defined in the configuration.
   *
   * @param nodeId the nodeId for this application
   */
  static NodeIdProvider staticProvider(final int nodeId) {
    if (nodeId < 0) {
      throw new IllegalArgumentException("Invalid nodeId: " + nodeId);
    }
    return new NodeIdProvider() {

      @Override
      public NodeInstance currentNodeInstance() {
        return new NodeInstance(nodeId);
      }

      @Override
      public CompletableFuture<Boolean> isValid() {
        return CompletableFuture.completedFuture(true);
      }
    };
  }
}
