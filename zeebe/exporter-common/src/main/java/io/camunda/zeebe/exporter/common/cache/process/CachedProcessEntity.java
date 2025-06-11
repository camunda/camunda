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

public record CachedProcessEntity(
    String name,
    String versionTag,
    String version,
    List<String> callElementIds,
    Map<String, String> flowNodesMap) {
  public CachedProcessEntity(
      final String name,
      final String versionTag,
      final List<String> callElementIds,
      final Map<String, String> flowNodesMap) {
    this(name, versionTag, null, callElementIds, flowNodesMap);
  }
}
