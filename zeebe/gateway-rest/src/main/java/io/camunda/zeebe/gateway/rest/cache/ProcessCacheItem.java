/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.cache;

import java.util.Collections;
import java.util.Map;

public record ProcessCacheItem(Map<String, String> elementIdNameMap) {

  public static final ProcessCacheItem EMPTY = new ProcessCacheItem(Collections.emptyMap());

  public String getElementName(final String elementId) {
    return elementIdNameMap.getOrDefault(elementId, elementId);
  }
}
