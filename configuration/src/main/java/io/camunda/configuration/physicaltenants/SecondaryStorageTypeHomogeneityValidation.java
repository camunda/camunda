/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

/**
 * Cross-tenant rule: all physical tenants must resolve to a single secondary-storage
 * <em>compatibility class</em>.
 *
 * <p>The classes are:
 *
 * <ul>
 *   <li>{@link CompatibilityClass#DOCUMENT} — Elasticsearch and OpenSearch. These may mix freely
 *       across tenants.
 *   <li>{@link CompatibilityClass#RELATIONAL} — RDBMS. May not mix with document stores.
 *   <li>{@link CompatibilityClass#NONE} — no secondary storage. Its own class: a {@code none}
 *       tenant alongside a storage-backed tenant is rejected.
 * </ul>
 *
 * <p>A cluster that spans more than one class is rejected. Note: even an ES/OS mix may not be
 * tolerated by every {@code @ConditionalOnSecondaryStorageType} bean today; tightening this rule
 * (or fixing those beans) is tracked separately and is out of scope here.
 */
@NullMarked
class SecondaryStorageTypeHomogeneityValidation implements CrossTenantValidation {

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    if (resolvedByTenant.size() <= 1) {
      // a single tenant trivially shares one compatibility class with itself
      return;
    }

    final Map<CompatibilityClass, List<String>> tenantsByClass = new LinkedHashMap<>();
    final Map<String, SecondaryStorageType> typeByTenant = new LinkedHashMap<>();
    resolvedByTenant.forEach(
        (tenantId, camunda) -> {
          final SecondaryStorageType type = camunda.getData().getSecondaryStorage().getType();
          typeByTenant.put(tenantId, type);
          tenantsByClass.computeIfAbsent(classOf(type), k -> new ArrayList<>()).add(tenantId);
        });

    if (tenantsByClass.size() > 1) {
      // report the actual storage types per tenant (elasticsearch/opensearch/rdbms/none) rather
      // than the internal compatibility-class names, which are not part of the operator vocabulary
      final String breakdown =
          typeByTenant.entrySet().stream()
              .map(entry -> entry.getKey() + "=" + entry.getValue())
              .collect(Collectors.joining(", "));
      throw new UnifiedConfigurationException(
          "Physical tenants must all use a compatible secondary-storage type. Elasticsearch and "
              + "OpenSearch may be mixed, but RDBMS and 'none' may not be mixed with other types. "
              + "Found incompatible storage types across tenants: "
              + breakdown);
    }
  }

  private static CompatibilityClass classOf(final SecondaryStorageType type) {
    return switch (type) {
      case elasticsearch, opensearch -> CompatibilityClass.DOCUMENT;
      case rdbms -> CompatibilityClass.RELATIONAL;
      case none -> CompatibilityClass.NONE;
    };
  }

  private enum CompatibilityClass {
    DOCUMENT,
    RELATIONAL,
    NONE
  }
}
