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
import io.camunda.configuration.ExporterArgsMergers;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger.ExporterIsolationClaim;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
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
 * {@link ExporterConfigMerger#isolationClaims(Map)} (discovered with {@link ExporterArgsMergers},
 * matched to the entry's {@code className}), and this rule just groups every claim by {@code
 * (domain, key)} and rejects any resource claimed by more than one tenant. It therefore covers the
 * index and lifecycle-policy domains today, and any future domain, with no change here. An exporter
 * whose class ships no merger — or whose merger declares no claims — is silently skipped, the same
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
 * <p><b>Ordering:</b> this rule reads each tenant's resolved exporter map, so {@link
 * PhysicalTenantResolver} must run it only <em>after</em> {@link
 * PhysicalTenantExporterConfigurations#narrowToAssigned}. Before narrowing, a tenant still carries
 * every root catalog entry — including the ones it never assigned — so a single root-declared
 * generic exporter would appear to be claimed by every tenant at once and this rule would reject a
 * perfectly isolated deployment.
 */
@NullMarked
final class GenericExporterIsolationValidation implements CrossTenantValidation {

  private final Supplier<List<ExporterConfigMerger>> mergers;

  GenericExporterIsolationValidation() {
    // discovery is deferred to validate(): this rule sits in a static rule list, and resolving the
    // SPI while that list is class-initialized would surface a classpath problem as an
    // ExceptionInInitializerError instead of the boot error the resolver reports for everything
    // else
    this(ExporterArgsMergers::load);
  }

  @VisibleForTesting
  GenericExporterIsolationValidation(final Supplier<List<ExporterConfigMerger>> mergers) {
    this.mergers = mergers;
  }

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    if (resolvedByTenant.size() <= 1) {
      // a single tenant cannot collide with anything
      return;
    }

    final List<ExporterConfigMerger> loadedMergers = mergers.get();

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
                      claimsOf(loadedMergers, tenantId, exporterId, exporter)
                          .forEach(
                              claim ->
                                  tenantsByResource
                                      .computeIfAbsent(
                                          ResourceIdentity.of(claim),
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

  private static Set<ExporterIsolationClaim> claimsOf(
      final List<ExporterConfigMerger> mergers,
      final String tenantId,
      final String exporterId,
      final Exporter exporter) {
    final String context =
        String.format("exporter '%s' of physical tenant '%s'", exporterId, tenantId);
    // the shared lookup also enforces the exactly-one-claimant rule, so an ambiguous exporter class
    // fails here too rather than silently having one of its two mergers decide its claims
    final ExporterConfigMerger merger =
        ExporterArgsMergers.find(mergers, exporter.getClassName(), context);
    if (merger == null) {
      // no class name, or a class shipping no merger: nothing to introspect — skip, do not guess
      return Set.of();
    }
    return ExporterArgsMergers.isolationClaims(merger, exporter.getArgs(), context);
  }

  /** The collision identity of a claimed resource: exporters collide iff both fields are equal. */
  private record ResourceIdentity(String domain, Map<String, Object> identity) {

    /**
     * Freezes the identity a merger returned: it becomes part of a map key here, and the SPI does
     * not promise an immutable map, so a merger holding on to what it returned could otherwise
     * change this key's {@code hashCode} out from under the grouping.
     */
    static ResourceIdentity of(final ExporterIsolationClaim claim) {
      return new ResourceIdentity(
          claim.domain(), ExporterArgsMergers.immutableCopy(claim.identity()));
    }
  }

  /** The tenants claiming one resource, plus a human rendering of it for the error message. */
  private record ClaimedResource(String description, Set<String> tenants) {
    ClaimedResource(final String description) {
      this(description, new LinkedHashSet<>());
    }
  }
}
