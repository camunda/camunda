/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.cache.process;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CachedProcessEntity(
    String name,
    int version,
    String versionTag,
    List<String> callElementIds,
    Map<String, String> flowNodesMap,
    boolean hasUserTasks,
    Map<String, Map<String, String>> elementExtensionProperties,
    Set<String> adHocActivityIds) {

  /**
   * Backwards-compatible constructor that defaults {@code adHocActivityIds} to an empty set, so
   * existing call sites that do not compute the ad-hoc activity set keep compiling.
   */
  public CachedProcessEntity(
      final String name,
      final int version,
      final String versionTag,
      final List<String> callElementIds,
      final Map<String, String> flowNodesMap,
      final boolean hasUserTasks,
      final Map<String, Map<String, String>> elementExtensionProperties) {
    this(
        name,
        version,
        versionTag,
        callElementIds,
        flowNodesMap,
        hasUserTasks,
        elementExtensionProperties,
        Set.of());
  }
}
