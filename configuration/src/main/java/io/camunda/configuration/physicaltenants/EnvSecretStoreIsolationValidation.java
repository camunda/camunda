/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Secrets.EnvStore;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Cross-tenant rule: no two physical tenants may configure environment-variable secret stores whose
 * prefixes overlap, or one tenant would resolve the other's secrets.
 *
 * <p>Overlap is broader than equality, because a secret name is appended to the prefix unchecked.
 * With {@code SECRET_} for one tenant and {@code SECRET_PT1_} for another, the first resolves the
 * second's {@code SECRET_PT1_API_KEY} as its secret {@code PT1_API_KEY}. A tenant that inherits the
 * root store shares the root prefix with the {@code default} tenant, so it must override it.
 *
 * <p>Blank prefixes are skipped: they would overlap every other prefix, and startup refuses them
 * per store with a message naming the missing setting.
 */
@NullMarked
class EnvSecretStoreIsolationValidation implements CrossTenantValidation {

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    final List<TenantPrefix> prefixes =
        resolvedByTenant.entrySet().stream()
            .flatMap(
                tenant ->
                    tenant.getValue().getSecrets().getStores().getEnv().values().stream()
                        .map(EnvStore::getPrefix)
                        .filter(prefix -> !prefix.isBlank())
                        .map(prefix -> new TenantPrefix(tenant.getKey(), prefix)))
            .toList();

    final List<String> conflicts = new ArrayList<>();
    for (int i = 0; i < prefixes.size(); i++) {
      for (int j = i + 1; j < prefixes.size(); j++) {
        final TenantPrefix first = prefixes.get(i);
        final TenantPrefix second = prefixes.get(j);
        if (first.overlaps(second)) {
          conflicts.add(first.describe() + " and " + second.describe());
        }
      }
    }

    if (!conflicts.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Physical tenants must not configure overlapping environment-variable secret store "
              + "prefixes, or one tenant would resolve another's secrets. Give every tenant a "
              + "distinct prefix under camunda.physical-tenants.<id>.secrets.stores.env.<store>"
              + ".prefix that does not start with another tenant's prefix. Conflicts: "
              + String.join("; ", conflicts));
    }
  }

  private record TenantPrefix(String tenantId, String prefix) {

    boolean overlaps(final TenantPrefix other) {
      return !tenantId.equals(other.tenantId)
          && (prefix.startsWith(other.prefix) || other.prefix.startsWith(prefix));
    }

    String describe() {
      return "'%s' (prefix '%s')".formatted(tenantId, prefix);
    }
  }
}
