/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache.process;

import java.util.List;

public record CachedProcessEntity(
    String name, String versionTag, String version, List<String> callElementIds) {
  public CachedProcessEntity(
      final String name, final String versionTag, final List<String> callElementIds) {
    this(name, versionTag, null, callElementIds);
  }
}
