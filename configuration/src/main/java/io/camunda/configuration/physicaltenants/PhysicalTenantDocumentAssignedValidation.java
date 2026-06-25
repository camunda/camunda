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
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Cross-tenant rule: every non-default physical tenant that has document stores in its resolved
 * catalog must declare a non-empty {@code document.assigned} list referencing only known store ids.
 * Without it a tenant would silently inherit the full root catalog, including stores intended for
 * other tenants. The {@code default} tenant and tenants with no stores are exempt.
 */
@NullMarked
class PhysicalTenantDocumentAssignedValidation implements CrossTenantValidation {

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    final List<String> errors = new ArrayList<>();

    resolvedByTenant.forEach(
        (tenantId, camunda) -> {
          if (PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId)) {
            return;
          }

          final Document doc = camunda.getDocument();

          final Set<String> knownStoreIds = new LinkedHashSet<>();
          knownStoreIds.addAll(doc.getAws().keySet());
          knownStoreIds.addAll(doc.getGcp().keySet());
          knownStoreIds.addAll(doc.getAzure().keySet());
          knownStoreIds.addAll(doc.getLocal().keySet());
          knownStoreIds.addAll(doc.getInMemory().keySet());

          if (knownStoreIds.isEmpty()) {
            // no document stores in catalog — nothing to assign, skip
            return;
          }

          final List<String> assigned = doc.getAssigned();

          if (assigned.isEmpty()) {
            errors.add(
                String.format(
                    "non-default physical tenant '%s' must declare a non-empty "
                        + "'camunda.physical-tenants.%s.document.assigned' selecting which "
                        + "document stores are available to it",
                    tenantId, tenantId));
            return;
          }

          // Blank entries (e.g. `- ""` in yaml) must be caught here — they would otherwise fall
          // through to the unknown-id check and render as `[]`, giving a confusing message.
          if (assigned.stream().anyMatch(String::isBlank)) {
            errors.add(
                String.format(
                    "physical tenant '%s' declares a blank entry in "
                        + "'camunda.physical-tenants.%s.document.assigned'; "
                        + "every id must be a non-blank store id",
                    tenantId, tenantId));
            return;
          }

          final List<String> unknown =
              assigned.stream()
                  .filter(id -> !knownStoreIds.contains(Document.normalizeStoreId(id)))
                  .distinct()
                  .toList();
          if (!unknown.isEmpty()) {
            errors.add(
                String.format(
                    "physical tenant '%s' assigns unknown document store id(s) %s in "
                        + "'camunda.physical-tenants.%s.document.assigned'; "
                        + "known store ids are %s",
                    tenantId, unknown, tenantId, knownStoreIds));
          }
        });

    if (!errors.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Invalid physical-tenant document store assignment: " + String.join("; ", errors));
    }
  }
}
