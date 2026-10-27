/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.cache;

/**
 * Cached process definition metadata used by search readers to enrich query results.
 *
 * <p>This is intentionally minimal and does not include BPMN parsing; it only caches fields that
 * can be retrieved from the search index.
 */
public record ProcessCacheItem(
    String processDefinitionId, String processName, Integer version, String tenantId) {

  public static final ProcessCacheItem EMPTY = new ProcessCacheItem(null, null, null, null);

  public boolean isEmpty() {
    return this == EMPTY;
  }
}
