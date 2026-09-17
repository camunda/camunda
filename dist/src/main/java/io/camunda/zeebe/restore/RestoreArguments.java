/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.restore.ClusterRestore.TargetDataPolicy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The restore application's command-line arguments.
 *
 * <p>Shaped after {@code ClusterConfigurationManagementRequest.ClusterRestoreRequest}, so the same
 * restore is expressible here and at {@code /cluster/v2/restore}: one cluster-wide selection, plus
 * per-tenant overrides that replace it for the tenants they name.
 *
 * <pre>
 *   --backupId=27                     restore every targeted tenant from checkpoint 27
 *   --from=... --to=...               resolve each targeted tenant's own restore point in that range
 *   --tenantId=tenanta                target that tenant alone (default: the default tenant)
 *   --allTenants                      target every configured physical tenant
 *   --override.tenanta.backupId=31    tenanta restores from 31 instead of the cluster-wide selection
 * </pre>
 *
 * <p>Bound rather than read with {@code @Value} so every flag accepts the relaxed spellings Spring
 * Boot allows — {@code --tenantId}, {@code --tenant-id} and {@code TENANT_ID} are one flag — and so
 * the override map can be keyed by tenant ids that are not known until the configuration is read.
 */
@ConfigurationProperties
@NullMarked
public class RestoreArguments {

  private @Nullable List<Long> backupId;
  private @Nullable Instant from;
  private @Nullable Instant to;
  private @Nullable String tenantId;
  private @Nullable String allTenants;
  private Map<String, TenantOverride> override = new LinkedHashMap<>();

  /**
   * What this run may do to data already in the data directory.
   *
   * <p>Naming a tenant with {@code --tenantId} is asking to replace that tenant on a node whose
   * other tenants are live, so their data stays and the named tenant's own directory is cleared.
   * Every other invocation — the default tenant, or {@code --allTenants} — is the recovery of a
   * node meant to start from nothing, and keeps the long-standing requirement that the data
   * directory be empty.
   */
  public TargetDataPolicy targetDataPolicy() {
    return tenantId != null && !allTenantsRequested()
        ? TargetDataPolicy.REPLACE_SELECTED
        : TargetDataPolicy.REQUIRE_EMPTY;
  }

  /**
   * The physical tenants this run restores, and what each restores from.
   *
   * @param configuredPhysicalTenantIds every physical tenant this cluster is configured with
   * @throws IllegalArgumentException if the arguments name no tenant, contradict each other, or
   *     would silently leave a configured tenant unrestored
   */
  public Map<String, RestoreSelection> selectionPerPhysicalTenant(
      final Set<String> configuredPhysicalTenantIds) {
    final Map<String, RestoreSelection> selection = new LinkedHashMap<>();
    for (final var physicalTenantId : targetedPhysicalTenants(configuredPhysicalTenantIds)) {
      selection.put(physicalTenantId, selectionFor(physicalTenantId));
    }
    return selection;
  }

  /**
   * Which physical tenants this run targets: the one named by {@code --tenantId}, every configured
   * one under {@code --allTenants}, or — naming neither — the default tenant.
   *
   * <p>Defaulting to the default tenant on a multi-tenant cluster restores that tenant alone. That
   * is a partial restore, not a truncated full one: the other tenants keep their data and their
   * entry in the topology file (see {@code ClusterRestore#restoreTopologyFile}), and only the
   * default tenant's own directory has to be free.
   */
  private Set<String> targetedPhysicalTenants(final Set<String> configuredPhysicalTenantIds) {
    if (allTenantsRequested() && tenantId != null) {
      throw new IllegalArgumentException(
          "Expected either --allTenants or --tenantId, but got both (--tenantId=%s)"
              .formatted(tenantId));
    }
    if (allTenantsRequested()) {
      return configuredPhysicalTenantIds;
    }
    if (tenantId != null) {
      if (!configuredPhysicalTenantIds.contains(tenantId)) {
        throw new IllegalArgumentException(
            "Cannot restore physical tenant '%s': not configured in this cluster, which has %s"
                .formatted(tenantId, new TreeSet<>(configuredPhysicalTenantIds)));
      }
      return Set.of(tenantId);
    }
    // The default tenant is not a configuration choice: PhysicalTenantResolver#of synthesizes it
    // from the root configuration whenever no `camunda.physical-tenants.default.*` override is
    // declared, so configuredPhysicalTenantIds always contains it. Nothing to guard here.
    return Set.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  /** The override for this tenant if it has one, otherwise the cluster-wide selection. */
  private RestoreSelection selectionFor(final String physicalTenantId) {
    final var tenantOverride = override.get(physicalTenantId);
    if (tenantOverride == null) {
      return selection(backupId, from, to);
    }
    return selection(tenantOverride.backupId, tenantOverride.from, tenantOverride.to);
  }

  /**
   * Built through the canonical constructor rather than the named factories, so {@code
   * RestoreSelection}'s own rejection of naming both a backup id and a time range applies here.
   * {@code --backupId} has always been documented as mutually exclusive with {@code --from}/{@code
   * --to}; it used to win silently, which restored to a checkpoint the operator had not asked for.
   */
  private static RestoreSelection selection(
      final @Nullable List<Long> backupIds,
      final @Nullable Instant from,
      final @Nullable Instant to) {
    return new RestoreSelection(backupIds == null ? List.of() : backupIds, from, to);
  }

  public @Nullable List<Long> getBackupId() {
    return backupId;
  }

  public void setBackupId(final @Nullable List<Long> backupId) {
    this.backupId = backupId;
  }

  public @Nullable Instant getFrom() {
    return from;
  }

  public void setFrom(final @Nullable Instant from) {
    this.from = from;
  }

  public @Nullable Instant getTo() {
    return to;
  }

  public void setTo(final @Nullable Instant to) {
    this.to = to;
  }

  public @Nullable String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final @Nullable String tenantId) {
    this.tenantId = tenantId;
  }

  /**
   * Whether every configured physical tenant was requested.
   *
   * <p>Bound as a {@code String}, not a {@code boolean}, so {@code --allTenants} works without a
   * value: Spring gives a valueless command-line option the empty string, which no boolean
   * conversion accepts, and a {@code Boolean} would bind it to {@code null} — indistinguishable
   * from the flag being absent, so the flag would silently do nothing. Interpreting the raw value
   * here means the flag binds the same on every start path, rather than only where an argument
   * rewrite happens to run.
   */
  public boolean allTenantsRequested() {
    return allTenants != null && (allTenants.isBlank() || Boolean.parseBoolean(allTenants));
  }

  public @Nullable String getAllTenants() {
    return allTenants;
  }

  public void setAllTenants(final @Nullable String allTenants) {
    this.allTenants = allTenants;
  }

  public Map<String, TenantOverride> getOverride() {
    return override;
  }

  public void setOverride(final Map<String, TenantOverride> override) {
    this.override = override;
  }

  /** One physical tenant's replacement for the cluster-wide selection. */
  @NullMarked
  public static class TenantOverride {

    private @Nullable List<Long> backupId;
    private @Nullable Instant from;
    private @Nullable Instant to;

    public @Nullable List<Long> getBackupId() {
      return backupId;
    }

    public void setBackupId(final @Nullable List<Long> backupId) {
      this.backupId = backupId;
    }

    public @Nullable Instant getFrom() {
      return from;
    }

    public void setFrom(final @Nullable Instant from) {
      this.from = from;
    }

    public @Nullable Instant getTo() {
      return to;
    }

    public void setTo(final @Nullable Instant to) {
      this.to = to;
    }
  }
}
