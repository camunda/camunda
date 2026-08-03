/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Exporter;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger.ExporterIsolationClaim;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Cross-tenant rule: no two physical tenants may have <em>generic</em> exporters that claim the
 * same isolated resource — the same {@link ExporterIsolationClaim} {@code (domain, key)}. Sharing
 * one means the tenants collide on that resource: for the {@code index-write-target} domain they
 * would double-write into the same indices, for the {@code lifecycle-policy} domain they would
 * overwrite each other's cluster-global ILM/ISM policy. This is the generic-exporter analog of
 * {@link SecondaryStorageIsolationValidation}/{@link RetentionPolicyIsolationValidation}, which
 * guard the autoconfigured exporters' secondary storage and its policy.
 *
 * <p>The rule is <em>domain-agnostic</em>: an exporter declares which resources it occupies via
 * {@link ExporterConfigMerger#isolationClaims(Map)} (discovered with {@link ServiceLoader}, matched
 * to the entry's {@code className}), and this rule just groups every claim by {@code (domain, key)}
 * and rejects any resource claimed by more than one tenant. It therefore covers the index and
 * lifecycle-policy domains today, and any future domain, with no change here. An exporter whose
 * class ships no merger — or whose merger declares no claims — is silently skipped, the same
 * best-effort stance {@link StorageIdentity} documents for what it cannot statically compare.
 *
 * <p>"Generic" excludes the autoconfigured {@code camundaexporter}/{@code rdbms} ({@link
 * PhysicalTenantExporterConfigurations#AUTOCONFIGURED_EXPORTER_IDS}): their destination is the
 * tenant's secondary storage, already isolated by the two validations named above. A
 * CamundaExporter <em>class</em> declared under a non-autoconfigured id (the multi-region
 * duplication setup) is checked here — exactly the "camundaexporter only when the id is not {@code
 * camundaexporter}" intent.
 *
 * <p>Colliding tenants are reported as a single grouped error, not O(n²) pairwise messages;
 * single-tenant deployments are a no-op. The synthesized {@code default} tenant participates like
 * any other.
 *
 * <p><b>Dormant:</b> not yet invoked from {@link PhysicalTenantResolver} — like {@link
 * PhysicalTenantExporterAssignedValidation} it is gated on <a
 * href="https://github.com/camunda/camunda/issues/56652">#56652</a> and must run only
 * <em>after</em> {@link PhysicalTenantExporterConfigurations#narrowToAssigned}. Before narrowing,
 * every tenant inherits the whole root catalog, so a single root-declared generic exporter claims
 * the same resource in every tenant and this rule would false-positive on the interim inherit-all
 * behavior. See {@link PhysicalTenantExporterConfigurations} for the full activation recipe.
 */
@NullMarked
final class GenericExporterIsolationValidation implements CrossTenantValidation {

  private final List<ExporterConfigMerger> mergers;

  GenericExporterIsolationValidation() {
    this(
        ServiceLoader.load(ExporterConfigMerger.class).stream()
            .map(ServiceLoader.Provider::get)
            .toList());
  }

  GenericExporterIsolationValidation(final List<ExporterConfigMerger> mergers) {
    this.mergers = mergers;
  }

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    if (resolvedByTenant.size() <= 1) {
      // a single tenant cannot collide with anything
      return;
    }

    final Map<ResourceIdentity, ClaimedResource> tenantsByResource = new LinkedHashMap<>();
    resolvedByTenant.forEach(
        (tenantId, camunda) ->
            camunda
                .getData()
                .getExporters()
                .forEach(
                    (exporterId, exporter) -> {
                      if (PhysicalTenantExporterConfigurations.AUTOCONFIGURED_EXPORTER_IDS.contains(
                          exporterId)) {
                        // autoconfigured exporters write to secondary storage, collisions are
                        // already checked in other classes
                        return;
                      }
                      claimsOf(exporter)
                          .forEach(
                              claim ->
                                  tenantsByResource
                                      .computeIfAbsent(
                                          new ResourceIdentity(claim.domain(), claim.identity()),
                                          k -> new ClaimedResource(claim.description()))
                                      .tenants()
                                      .add(tenantId));
                    }));

    final List<String> collisions = new ArrayList<>();
    tenantsByResource.forEach(
        (identity, resource) -> {
          if (resource.tenants().size() > 1) {
            collisions.add(
                String.format(
                    "tenants %s share the same %s", resource.tenants(), resource.description()));
          }
        });

    if (!collisions.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Physical tenants must not share generic-exporter resources, or they would silently "
              + "collide — two exporters writing into the same indices, or managing the same "
              + "cluster-global lifecycle policy. Give each tenant that shares a cluster a distinct "
              + "index prefix and lifecycle-policy name. Conflicts: "
              + String.join("; ", collisions));
    }
  }

  private Set<ExporterIsolationClaim> claimsOf(final Exporter exporter) {
    final String className = exporter.getClassName();
    if (className == null) {
      // tenant-private entries without a class cannot be introspected — skip, do not guess
      return Set.of();
    }
    // a class with two claimant mergers already fails startup in
    // PhysicalTenantExporterConfigurations#apply (which runs first), so first-match is safe here
    return mergers.stream()
        .filter(m -> m.supports(className))
        .findFirst()
        .map(m -> m.isolationClaims(exporter.getArgs() == null ? Map.of() : exporter.getArgs()))
        .orElseGet(Set::of);
  }

  /** The collision identity of a claimed resource: exporters collide iff both fields are equal. */
  private record ResourceIdentity(String domain, Map<String, Object> identity) {}

  /** The tenants claiming one resource, plus a human rendering of it for the error message. */
  private record ClaimedResource(String description, Set<String> tenants) {
    ClaimedResource(final String description) {
      this(description, new LinkedHashSet<>());
    }
  }
}
