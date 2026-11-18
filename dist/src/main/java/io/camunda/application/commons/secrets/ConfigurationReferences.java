/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.configuration.ConfigurationSecretReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The secret reference behind each resolved configuration property, per physical tenant, so that a
 * value can be read again after startup.
 */
public final class ConfigurationReferences {

  private final Map<String, Map<String, ConfigurationSecretReference>> byPhysicalTenant =
      new ConcurrentHashMap<>();

  /**
   * Returns the references of the given physical tenant, keyed by the property that held them, such
   * as {@code data.secondary-storage.elasticsearch.password}.
   */
  public Map<String, ConfigurationSecretReference> forPhysicalTenant(
      final String physicalTenantId) {
    return byPhysicalTenant.getOrDefault(physicalTenantId, Map.of());
  }

  void put(
      final String physicalTenantId, final Map<String, ConfigurationSecretReference> references) {
    byPhysicalTenant.put(physicalTenantId, Map.copyOf(references));
  }

  void remove(final String physicalTenantId) {
    byPhysicalTenant.remove(physicalTenantId);
  }
}
