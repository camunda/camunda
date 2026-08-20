/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Document;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Validates {@code document.assigned} per physical tenant at startup. {@code assigned} is read from
 * the environment via {@link Binder} — not a field on the {@link Document} POJO.
 *
 * <p>The synthesized {@code default} (absent from {@code camunda.physical-tenants.*}) is exempt; an
 * explicitly declared {@code default} is validated like any other tenant.
 */
@NullMarked
final class PhysicalTenantDocumentAssignedValidation {

  private static final String PHYSICAL_TENANTS_PREFIX = Camunda.PREFIX + ".physical-tenants";

  private PhysicalTenantDocumentAssignedValidation() {}

  static void validate(
      final Environment environment,
      final Map<String, Camunda> resolvedByTenant,
      final Set<String> explicitlyDeclared) {
    final Binder binder = Binder.get(environment);
    final List<String> errors = new ArrayList<>();

    resolvedByTenant.forEach(
        (tenantId, camunda) -> {
          if (PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId)
              && !explicitlyDeclared.contains(tenantId)) {
            // synthesized default is exempt; explicitly declared default is validated like any
            // other
            return;
          }

          final Document doc = camunda.getDocument();

          final Set<String> knownStoreIds = new LinkedHashSet<>();
          doc.getAws().keySet().forEach(id -> knownStoreIds.add(Document.normalizeStoreId(id)));
          doc.getGcp().keySet().forEach(id -> knownStoreIds.add(Document.normalizeStoreId(id)));
          doc.getAzure().keySet().forEach(id -> knownStoreIds.add(Document.normalizeStoreId(id)));
          doc.getLocal().keySet().forEach(id -> knownStoreIds.add(Document.normalizeStoreId(id)));
          doc.getInMemory()
              .keySet()
              .forEach(id -> knownStoreIds.add(Document.normalizeStoreId(id)));

          if (knownStoreIds.isEmpty()) {
            return;
          }

          final List<String> assigned = bindAssigned(binder, tenantId);

          if (assigned == null || assigned.isEmpty()) {
            errors.add(
                String.format(
                    "physical tenant '%s' must declare a non-empty "
                        + "'camunda.physical-tenants.%s.document.assigned' selecting which "
                        + "document stores are available to it",
                    tenantId, tenantId));
            return;
          }

          // blank entries (e.g. `- ""` in yaml) would otherwise fall through to the unknown-id
          // check and render as `[]`, giving a confusing message
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

  /**
   * Rejects {@code camunda.document.assigned} at the root level. Without this check it would be
   * silently ignored, since narrowing reads only from the tenant prefix.
   */
  public static void validateRootAssignedAbsent(final Environment environment) {
    final boolean rootAssignedPresent =
        Binder.get(environment)
            .bind("camunda.document.assigned", Bindable.listOf(String.class))
            .isBound();
    if (rootAssignedPresent) {
      throw new UnifiedConfigurationException(
          "'camunda.document.assigned' must not be set at the root level. "
              + "Declare it per physical tenant under "
              + "'camunda.physical-tenants.<id>.document.assigned'.");
    }
  }

  private static @Nullable List<String> bindAssigned(final Binder binder, final String tenantId) {
    return binder
        .bind(
            PHYSICAL_TENANTS_PREFIX + "." + tenantId + ".document.assigned",
            Bindable.listOf(String.class))
        .orElse(null);
  }
}
