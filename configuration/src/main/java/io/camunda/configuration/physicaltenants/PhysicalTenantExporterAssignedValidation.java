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
import io.camunda.configuration.Exporter;
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
 * Validates the {@code data.exporters-assigned} manifest per physical tenant (ADR-0008 D1/D2/D5).
 * {@code exporters-assigned} is read from the environment via {@link Binder} — it is never a field
 * on the config POJO — and is the complete manifest of the generic exporters that run for a tenant
 * (catalog entries it inherits plus exporters it declares itself). The autoconfigured {@code
 * camundaexporter}/{@code rdbms} exporters are outside the catalog and must never be listed.
 *
 * <p>Per tenant, this rejects at boot (as a {@link UnifiedConfigurationException}):
 *
 * <ul>
 *   <li><b>mandatory-explicit:</b> an absent manifest whenever a generic exporter could apply (a
 *       non-empty root catalog, or a generic exporter declared by the tenant) — an empty list is
 *       valid and means "no generic exporters";
 *   <li><b>blank entry:</b> a blank id in the list;
 *   <li><b>exempt id:</b> an autoconfigured id ({@code camundaexporter}/{@code rdbms}) in the list;
 *   <li><b>unknown id:</b> an assigned id that is neither in the root catalog nor declared by the
 *       tenant;
 *   <li><b>configured-but-unassigned:</b> a generic exporter id the tenant declares under {@code
 *       data.exporters} but omits from {@code exporters-assigned} — configuring an exporter is not
 *       a way to activate it, and unassigning one is not a way to deactivate it.
 * </ul>
 *
 * <p>The {@code className}/{@code jarPath}-divergence boot error (assigning a root id but giving it
 * a different class) is enforced upstream in {@link PhysicalTenantExporterConfigurations#apply}.
 * This mirrors {@link PhysicalTenantDocumentAssignedValidation}; the synthesized {@code default}
 * tenant is exempt, an explicitly declared {@code default} is validated like any other tenant.
 *
 * <p><b>Dormant:</b> not yet called from {@link PhysicalTenantResolver} — wiring it in is gated on
 * <a href="https://github.com/camunda/camunda/issues/56652">#56652</a>; see {@link
 * PhysicalTenantExporterConfigurations} for the activation recipe and the reason for the gate.
 */
@NullMarked
final class PhysicalTenantExporterAssignedValidation {

  private static final String PHYSICAL_TENANTS_PREFIX = Camunda.PREFIX + ".physical-tenants";
  private static final String ROOT_ASSIGNED_KEY = Camunda.PREFIX + ".data.exporters-assigned";

  private PhysicalTenantExporterAssignedValidation() {}

  /**
   * Rejects {@code camunda.data.exporters-assigned} at the root level. Assignment is a per-tenant
   * isolation decision read only from the tenant prefix; a root-level key would be silently
   * ignored.
   */
  static void validateRootAssignedAbsent(final Environment environment) {
    final boolean rootAssignedPresent =
        Binder.get(environment).bind(ROOT_ASSIGNED_KEY, Bindable.listOf(String.class)).isBound();
    if (rootAssignedPresent) {
      throw new UnifiedConfigurationException(
          "'"
              + ROOT_ASSIGNED_KEY
              + "' must not be set at the root level. Declare it per physical tenant under "
              + "'camunda.physical-tenants.<id>.data.exporters-assigned'.");
    }
  }

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
          validateTenant(binder, tenantId, camunda, errors);
        });

    if (!errors.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Invalid physical-tenant exporter assignment: " + String.join("; ", errors));
    }
  }

  private static void validateTenant(
      final Binder binder,
      final String tenantId,
      final Camunda camunda,
      final List<String> errors) {
    // The generic-exporter universe (post-apply, pre-narrow): every resolved exporter id except the
    // autoconfigured ones — i.e. catalog entries the tenant inherits plus entries it declares.
    final Set<String> genericUniverse = new LinkedHashSet<>();
    camunda.getData().getExporters().keySet().stream()
        .filter(id -> !isAutoconfigured(id))
        .forEach(genericUniverse::add);

    // Ids the tenant declares itself (configures), from a targeted re-bind of the tenant prefix.
    final Set<String> tenantConfigured = new LinkedHashSet<>();
    bindTenantDeclared(binder, tenantId).keySet().stream()
        .filter(id -> !isAutoconfigured(id))
        .forEach(tenantConfigured::add);

    final List<String> assigned = bindAssigned(binder, tenantId);

    if (assigned == null) {
      if (!genericUniverse.isEmpty()) {
        errors.add(
            String.format(
                "physical tenant '%s' must declare "
                    + "'camunda.physical-tenants.%s.data.exporters-assigned' listing which generic "
                    + "exporters run for it (an empty list is valid and means \"no generic "
                    + "exporters\"); it is mandatory because a generic exporter could apply to this "
                    + "tenant — one is declared in the root catalog or by the tenant itself",
                tenantId, tenantId));
      }
      return;
    }

    // blank entries would otherwise fall through and render confusingly in the unknown-id message
    if (assigned.stream().anyMatch(String::isBlank)) {
      errors.add(
          String.format(
              "physical tenant '%s' declares a blank entry in "
                  + "'camunda.physical-tenants.%s.data.exporters-assigned'; every id must be a "
                  + "non-blank exporter id",
              tenantId, tenantId));
      return;
    }

    final List<String> exemptListed =
        assigned.stream()
            .filter(PhysicalTenantExporterAssignedValidation::isAutoconfigured)
            .distinct()
            .toList();
    if (!exemptListed.isEmpty()) {
      errors.add(
          String.format(
              "physical tenant '%s' lists autoconfigured exporter id(s) %s in "
                  + "'camunda.physical-tenants.%s.data.exporters-assigned'; the autoconfigured %s "
                  + "exporters are always present when their secondary storage is configured and "
                  + "must not be assigned",
              tenantId,
              exemptListed,
              tenantId,
              PhysicalTenantExporterConfigurations.AUTOCONFIGURED_EXPORTER_IDS));
    }

    final Set<String> assignedIds = new LinkedHashSet<>(assigned);

    final List<String> unknown =
        assignedIds.stream()
            .filter(id -> !isAutoconfigured(id))
            .filter(id -> !genericUniverse.contains(id))
            .distinct()
            .toList();
    if (!unknown.isEmpty()) {
      errors.add(
          String.format(
              "physical tenant '%s' assigns unknown exporter id(s) %s in "
                  + "'camunda.physical-tenants.%s.data.exporters-assigned'; an assigned id must be "
                  + "declared either in the root catalog or by the tenant itself. Known generic "
                  + "exporter ids are %s",
              tenantId, unknown, tenantId, genericUniverse));
    }

    final List<String> configuredNotAssigned =
        tenantConfigured.stream().filter(id -> !assignedIds.contains(id)).distinct().toList();
    if (!configuredNotAssigned.isEmpty()) {
      errors.add(
          String.format(
              "physical tenant '%s' configures exporter id(s) %s under "
                  + "'camunda.physical-tenants.%s.data.exporters' but does not assign them in "
                  + "'exporters-assigned'; configuring an exporter is not a way to activate it, and "
                  + "unassigning one is not a way to deactivate it (runtime deactivation is the job "
                  + "of the exporter-disable management endpoint)",
              tenantId, configuredNotAssigned, tenantId));
    }
  }

  private static boolean isAutoconfigured(final String id) {
    // exporter ids are case-sensitive (#36444), so match the autoconfigured ids verbatim
    return PhysicalTenantExporterConfigurations.AUTOCONFIGURED_EXPORTER_IDS.contains(id);
  }

  private static Map<String, Exporter> bindTenantDeclared(
      final Binder binder, final String tenantId) {
    return binder
        .bind(
            PHYSICAL_TENANTS_PREFIX + "." + tenantId + ".data.exporters",
            Bindable.mapOf(String.class, Exporter.class))
        .orElseGet(Map::of);
  }

  private static @Nullable List<String> bindAssigned(final Binder binder, final String tenantId) {
    return binder
        .bind(
            PHYSICAL_TENANTS_PREFIX + "." + tenantId + ".data.exporters-assigned",
            Bindable.listOf(String.class))
        .orElse(null);
  }
}
