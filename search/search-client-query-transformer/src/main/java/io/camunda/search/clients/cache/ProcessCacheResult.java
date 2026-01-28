/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.cache;

import java.util.Map;

/**
 * Result wrapper around multiple cached process items, keyed by process definition key.
 *
 * <p>Missing or unloadable keys should be represented as {@link ProcessCacheItem#EMPTY}.
 */
public record ProcessCacheResult(Map<Long, ProcessCacheItem> items) {

  public ProcessCacheItem getProcessItem(final Long processDefinitionKey) {
    return items.getOrDefault(processDefinitionKey, ProcessCacheItem.EMPTY);
  }
}
