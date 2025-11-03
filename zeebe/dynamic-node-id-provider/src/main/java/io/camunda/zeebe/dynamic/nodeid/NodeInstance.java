/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

// id ~ integer from 0 to clusterSize
public record NodeInstance(int id /*, int version*/) {
  public NodeInstance {
    if (id < 0) {
      throw new IllegalArgumentException("id cannot be negative, was " + id);
    }
  }
}
