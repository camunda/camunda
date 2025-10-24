/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.cache;

import java.util.Collections;
import java.util.Map;

public record ProcessCacheResult(Map<Long, ProcessCacheItem> cachedProcesses) {

  public static final ProcessCacheResult EMPTY = new ProcessCacheResult(Collections.emptyMap());

  public String getElementName(final Long processDefinitionKey, final String elementId) {
    return getProcessItem(processDefinitionKey).getElementName(elementId);
  }

  public ProcessCacheItem getProcessItem(final Long processDefinitionKey) {
    return cachedProcesses.getOrDefault(processDefinitionKey, ProcessCacheItem.EMPTY);
  }

  public static ProcessCacheResult of(
      final Long processDefinitionKey,
      final String processName,
      final String elementId,
      final String cachedName) {
    return new ProcessCacheResult(
        Map.of(
            processDefinitionKey,
            new ProcessCacheItem(processName, Map.of(elementId, cachedName))));
  }

  public boolean isEmpty() {
    return cachedProcesses.isEmpty();
  }
}
