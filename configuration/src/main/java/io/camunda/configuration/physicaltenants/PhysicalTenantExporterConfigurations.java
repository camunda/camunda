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
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Per-tenant {@code data.exporters} resolution (ADR-0008 §2/§5): recomputes a physical tenant's
 * exporter entries after the generic two-bind, whose native {@code MapBinder} semantics build a
 * <em>fresh</em> entry from only the tenant's own keys (dropping the root entry's {@code className}
 * and untouched args). For every exporter id the tenant touches that is also declared in the root
 * catalog, this step instead:
 *
 * <ul>
 *   <li>rejects a {@code className}/{@code jarPath} diverging from the root entry (assigning an id
 *       means running root's exporter — a different class belongs under a new, tenant-private id);
 *   <li>inherits root's {@code className}/{@code jarPath};
 *   <li>deep-merges the args if the exporter class ships an {@link ExporterConfigMerger}
 *       (discovered via {@link ServiceLoader}), and otherwise takes the tenant's args exactly as
 *       declared (whole-map replace — partial inheritance is not offered for classes whose config
 *       model we cannot introspect).
 * </ul>
 *
 * <p>Entries the tenant does not touch stay inherited from root unchanged; ids the tenant declares
 * that root does not know are tenant-private and taken exactly as declared. The autoconfigured
 * exporters ({@value BrokerBasedPropertiesOverride#CAMUNDA_EXPORTER_NAME} and {@value
 * BrokerBasedPropertiesOverride#RDBMS_EXPORTER_NAME}) sit outside the catalog: their configuration
 * is derived downstream from the tenant's secondary-storage properties, and args-tuning declared
 * for them is taken as-is.
 *
 * <p><b>The {@code data.exporters-assigned} manifest (ADR-0008 D1/D2/D5) is implemented but not yet
 * wired into {@link PhysicalTenantResolver}, gated on <a
 * href="https://github.com/camunda/camunda/issues/56652">#56652</a>:</b> its mandatory-explicit
 * validation, the exempt/unknown/configured-but-unassigned boot errors, and the root-level
 * placement check live in {@link PhysicalTenantExporterAssignedValidation}; the narrowing of
 * unassigned catalog entries out of the tenant's resolved map lives in {@link #narrowToAssigned}.
 * Both are dormant on purpose: as long as the initial dynamic cluster configuration derives the
 * per-partition exporter <em>enable</em> state from the root {@code BrokerCfg} only, a
 * narrowed-away id would remain enabled on the tenant's partitions and be instantiated as a {@code
 * BlockingExporter}, stalling exporting and log compaction — so the interim inherit-all behavior
 * stands until #56652 flips the wire-in. To activate (from {@link PhysicalTenantResolver#of}): call
 * {@link PhysicalTenantExporterAssignedValidation#validateRootAssignedAbsent} before the tenant
 * loop, then — after the loop and once every tenant's {@link #apply} has run — {@link
 * PhysicalTenantExporterAssignedValidation#validate} for the whole resolved-by-tenant map, and only
 * then {@link #narrowToAssigned} for each tenant. Validation must precede narrowing: narrowing
 * removes ids, and validation checks assigned ids against the full pre-narrow universe. {@link
 * #apply} itself therefore changes only entry <em>contents</em>, never which ids exist for a
 * tenant.
 */
@NullMarked
final class PhysicalTenantExporterConfigurations {

  static final Set<String> AUTOCONFIGURED_EXPORTER_IDS =
      Set.of(
          BrokerBasedPropertiesOverride.CAMUNDA_EXPORTER_NAME,
          BrokerBasedPropertiesOverride.RDBMS_EXPORTER_NAME);

  private static final Logger LOG =
      LoggerFactory.getLogger(PhysicalTenantExporterConfigurations.class);
  private static final String PHYSICAL_TENANTS_PREFIX = Camunda.PREFIX + ".physical-tenants";

  private PhysicalTenantExporterConfigurations() {}

  /**
   * Recomputes {@code physicalTenant}'s {@code data.exporters} from the root catalog (the
   * resolver's authoritative, pre-overlay root {@link Camunda}) and the tenant's own declarations
   * (a targeted re-bind of {@code camunda.physical-tenants.<tenantId>.data.exporters}).
   */
  static void apply(
      final Camunda root,
      final Camunda physicalTenant,
      final String tenantId,
      final Environment environment) {
    final Map<String, Exporter> tenantDeclared = bindTenantDeclared(environment, tenantId);
    if (tenantDeclared.isEmpty()) {
      // the two-bind left every root entry untouched on this tenant — nothing to recompute
      return;
    }

    final Map<String, Exporter> catalog = root.getData().getExporters();
    final List<ExporterConfigMerger> mergers =
        ServiceLoader.load(ExporterConfigMerger.class).stream()
            .map(ServiceLoader.Provider::get)
            .toList();

    final Map<String, Exporter> resolved =
        new LinkedHashMap<>(physicalTenant.getData().getExporters());
    tenantDeclared.forEach(
        (exporterId, tenantEntry) -> {
          final Exporter rootEntry = catalog.get(exporterId);
          if (rootEntry == null || AUTOCONFIGURED_EXPORTER_IDS.contains(exporterId)) {
            // tenant-private exporter, or args-tuning of an autoconfigured exporter: taken exactly
            // as declared — which is what the two-bind already produced
            return;
          }
          validateNoClassDivergence(tenantId, exporterId, rootEntry, tenantEntry);
          final Exporter merged = new Exporter();
          merged.setClassName(rootEntry.getClassName());
          merged.setJarPath(rootEntry.getJarPath());
          merged.setArgs(resolveArgs(mergers, tenantId, exporterId, rootEntry, tenantEntry));
          resolved.put(exporterId, merged);
        });
    physicalTenant.getData().setExporters(resolved);
  }

  private static Map<String, Exporter> bindTenantDeclared(
      final Environment environment, final String tenantId) {
    return Binder.get(environment)
        .bind(
            PHYSICAL_TENANTS_PREFIX + "." + tenantId + ".data.exporters",
            Bindable.mapOf(String.class, Exporter.class))
        .orElseGet(Map::of);
  }

  /**
   * Overriding a root-declared exporter means running root's exporter: a tenant may restate the
   * root {@code className}/{@code jarPath} but never change them — a different class belongs under
   * a new, tenant-private exporter id (ADR-0008 §2).
   */
  private static void validateNoClassDivergence(
      final String tenantId,
      final String exporterId,
      final Exporter rootEntry,
      final Exporter tenantEntry) {
    requireSameIfRestated(
        tenantId, exporterId, "class-name", rootEntry.getClassName(), tenantEntry.getClassName());
    requireSameIfRestated(
        tenantId, exporterId, "jar-path", rootEntry.getJarPath(), tenantEntry.getJarPath());
  }

  private static void requireSameIfRestated(
      final String tenantId,
      final String exporterId,
      final String field,
      final @Nullable String rootValue,
      final @Nullable String tenantValue) {
    if (tenantValue != null && !Objects.equals(rootValue, tenantValue)) {
      throw new UnifiedConfigurationException(
          String.format(
              "Physical tenant '%s' declares '%s: %s' for exporter '%s', diverging from the root "
                  + "entry's '%s'. Overriding a root-declared exporter means running the root's "
                  + "exporter with adjusted args; to run a different exporter class, declare it "
                  + "under a new, tenant-private exporter id instead.",
              tenantId, field, tenantValue, exporterId, rootValue));
    }
  }

  private static @Nullable Map<String, Object> resolveArgs(
      final List<ExporterConfigMerger> mergers,
      final String tenantId,
      final String exporterId,
      final Exporter rootEntry,
      final Exporter tenantEntry) {
    final ExporterConfigMerger merger =
        findMerger(mergers, tenantId, exporterId, rootEntry.getClassName());
    if (merger == null) {
      // no merger for this class: whole-map replace, the tenant's args exactly as declared
      LOG.debug(
          "No ExporterConfigMerger for exporter '{}' (class '{}') of physical tenant '{}'; "
              + "the tenant's args replace the root args wholesale.",
          exporterId,
          rootEntry.getClassName(),
          tenantId);
      return tenantEntry.getArgs();
    }
    LOG.debug(
        "Deep-merging tenant args over root args for exporter '{}' (class '{}') of physical tenant "
            + "'{}' using merger '{}'.",
        exporterId,
        rootEntry.getClassName(),
        tenantId,
        merger.getClass().getName());
    // defensive: a merger is third-party SPI code, so hand it immutable copies rather than the
    // resolver's live args maps — the SPI contract forbids mutating its inputs, and this enforces
    // it
    final Map<String, Object> rootArgs = immutableCopy(rootEntry.getArgs());
    final Map<String, Object> tenantArgs = immutableCopy(tenantEntry.getArgs());
    try {
      return merger.merge(rootArgs, tenantArgs);
    } catch (final RuntimeException e) {
      throw new UnifiedConfigurationException(
          String.format(
              "Failed to merge exporter args for exporter '%s' of physical tenant '%s': %s",
              exporterId, tenantId, e.getMessage()),
          e);
    }
  }

  private static @Nullable ExporterConfigMerger findMerger(
      final List<ExporterConfigMerger> mergers,
      final String tenantId,
      final String exporterId,
      final @Nullable String className) {
    if (className == null) {
      return null;
    }
    final List<ExporterConfigMerger> claimants =
        mergers.stream().filter(merger -> merger.supports(className)).toList();
    if (claimants.size() > 1) {
      throw new UnifiedConfigurationException(
          String.format(
              "Multiple ExporterConfigMerger implementations claim exporter class '%s' "
                  + "(exporter '%s' of physical tenant '%s'): %s. Exactly one merger may support "
                  + "a given exporter class.",
              className,
              exporterId,
              tenantId,
              claimants.stream().map(m -> m.getClass().getName()).toList()));
    }
    return claimants.isEmpty() ? null : claimants.getFirst();
  }

  private static Map<String, Object> immutableCopy(final @Nullable Map<String, Object> args) {
    return args == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(args));
  }

  /**
   * Narrows {@code physicalTenant}'s resolved {@code data.exporters} to exactly the assigned
   * generic exporters plus the always-present autoconfigured entries (ADR-0008 D1/D2): every
   * catalog entry the tenant inherited but did not assign is removed, so the resolved map becomes
   * the tenant's complete generic-exporter manifest. The {@code exporters-assigned} list is read
   * from the environment — it is never a field on the config POJO. An absent manifest is a no-op
   * here ({@link PhysicalTenantExporterAssignedValidation} already rejects it at boot whenever a
   * generic exporter could apply); an explicit empty manifest keeps only the autoconfigured
   * entries.
   *
   * <p><b>Dormant:</b> not yet called from {@link PhysicalTenantResolver} — wiring it in is gated
   * on <a href="https://github.com/camunda/camunda/issues/56652">#56652</a>; see the class javadoc.
   */
  static void narrowToAssigned(
      final Camunda physicalTenant, final String tenantId, final Environment environment) {
    final List<String> assigned =
        Binder.get(environment)
            .bind(
                PHYSICAL_TENANTS_PREFIX + "." + tenantId + ".data.exporters-assigned",
                Bindable.listOf(String.class))
            .orElse(null);
    if (assigned == null) {
      return;
    }
    final Set<String> keep = new LinkedHashSet<>(AUTOCONFIGURED_EXPORTER_IDS);
    assigned.stream().filter(id -> !id.isBlank()).forEach(keep::add);
    // exporter ids are matched verbatim: they are case-sensitive (#36444), and no other id match in
    // this resolver (e.g. apply's catalog lookup) trims or normalizes, so neither does this one
    physicalTenant.getData().getExporters().keySet().removeIf(id -> !keep.contains(id));
  }
}
