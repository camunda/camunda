/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Document;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Cross-tenant rule: no two physical tenants may resolve to the same {@link DocumentStoreLocation};
 * sharing one means silently reading and writing to the same backing storage. In-memory stores are
 * excluded — they are ephemeral and process-local.
 */
@NullMarked
class DocumentStoreIsolationValidation implements CrossTenantValidation {

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    if (resolvedByTenant.size() <= 1) {
      return;
    }

    final Map<DocumentStoreLocation, Set<String>> tenantsByLocation = new LinkedHashMap<>();
    resolvedByTenant.forEach(
        (tenantId, camunda) -> {
          final Document doc = camunda.getDocument();
          doc.getAws()
              .forEach(
                  (storeId, store) ->
                      tenantsByLocation
                          .computeIfAbsent(
                              DocumentStoreLocation.aws(store), k -> new LinkedHashSet<>())
                          .add(tenantId));
          doc.getGcp()
              .forEach(
                  (storeId, store) ->
                      tenantsByLocation
                          .computeIfAbsent(
                              DocumentStoreLocation.gcp(store), k -> new LinkedHashSet<>())
                          .add(tenantId));
          doc.getAzure()
              .forEach(
                  (storeId, store) ->
                      tenantsByLocation
                          .computeIfAbsent(
                              DocumentStoreLocation.azure(store), k -> new LinkedHashSet<>())
                          .add(tenantId));
          doc.getLocal()
              .forEach(
                  (storeId, store) ->
                      tenantsByLocation
                          .computeIfAbsent(
                              DocumentStoreLocation.local(store), k -> new LinkedHashSet<>())
                          .add(tenantId));
          // in-memory stores are intentionally skipped: ephemeral and process-local
        });

    final List<String> collisions = new ArrayList<>();
    tenantsByLocation.forEach(
        (location, tenantIds) -> {
          if (tenantIds.size() > 1) {
            collisions.add(
                String.format(
                    "tenants %s share the same document store location [%s]",
                    tenantIds, location.describe()));
          }
        });

    if (!collisions.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Physical tenants must not share a document store location, or they would read and write "
              + "into the same backing storage. Use a distinct bucket, container, or path per "
              + "tenant. Conflicts: "
              + String.join("; ", collisions));
    }
  }
}
