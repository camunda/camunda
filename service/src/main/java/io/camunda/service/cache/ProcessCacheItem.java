/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.cache;

import io.camunda.service.cache.ProcessDefinitionProvider.ProcessCacheData;
import java.util.Collections;
import java.util.Map;

public record ProcessCacheItem(String processName, Map<String, String> elementIdNameMap) {

  public static final ProcessCacheItem EMPTY = new ProcessCacheItem(null, Collections.emptyMap());

  public String getProcessName() {
    return processName;
  }

  public String getElementName(final String elementId) {
    return elementIdNameMap.getOrDefault(elementId, elementId);
  }

  public static ProcessCacheItem from(ProcessCacheData processCacheData) {
    return new ProcessCacheItem(
        processCacheData.processName(),
        Collections.unmodifiableMap(processCacheData.elementIdNameMap()));
  }
}
