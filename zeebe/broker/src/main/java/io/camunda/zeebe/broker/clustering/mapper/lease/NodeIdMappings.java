/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering.mapper.lease;

import java.util.Map;

// Map: id -> version
public record NodeIdMappings(Map<String, Integer> mappings) {
  private static final NodeIdMappings EMPTY = new NodeIdMappings(Map.of());

  public static NodeIdMappings empty() {
    return EMPTY;
  }
}
