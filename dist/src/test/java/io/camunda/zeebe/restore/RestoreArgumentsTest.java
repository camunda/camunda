/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.restore.ClusterRestore.TargetDataPolicy;
import io.camunda.zeebe.restore.RestoreArguments.TenantOverride;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class RestoreArgumentsTest {

  private static final Set<String> ONE_TENANT = Set.of("default");
  private static final Set<String> TWO_TENANTS = Set.of("default", "tenanta");

  private static RestoreArguments arguments() {
    return new RestoreArguments();
  }

  @Nested
  class TenantTargeting {

    @Test
    void shouldTargetTheDefaultTenantOnASingleTenantCluster() {
      // given — no --tenant-id, which is every existing invocation
      final var arguments = arguments();

      // when / then
      assertThat(arguments.selectionPerPhysicalTenant(ONE_TENANT)).containsOnlyKeys("default");
    }

    @Test
    void shouldTargetTheDefaultTenantOnAMultiTenantCluster() {
      // given — no --tenant-id on a cluster that has more than the default tenant
      final var arguments = arguments();

      // when / then — a partial restore of the default tenant alone. The others keep their data
      // and their entry in the topology file, so this is not a truncated full restore.
      assertThat(arguments.selectionPerPhysicalTenant(TWO_TENANTS)).containsOnlyKeys("default");
    }

    @Test
    void shouldTargetOneTenantWhenNamed() {
      // given
      final var arguments = arguments();
      arguments.setTenantId("tenanta");

      // when / then — an explicit choice is trusted, unlike the implicit default above
      assertThat(arguments.selectionPerPhysicalTenant(TWO_TENANTS)).containsOnlyKeys("tenanta");
    }

    @Test
    void shouldTargetEveryTenantWhenAskedForAll() {
      // given
      final var arguments = arguments();
      arguments.setAllTenants("true");

      // when / then
      assertThat(arguments.selectionPerPhysicalTenant(TWO_TENANTS))
          .containsOnlyKeys("default", "tenanta");
    }

    @Test
    void shouldRejectAskingForBothOneTenantAndAll() {
      // given
      final var arguments = arguments();
      arguments.setTenantId("tenanta");
      arguments.setAllTenants("true");

      // when / then
      assertThatThrownBy(() -> arguments.selectionPerPhysicalTenant(TWO_TENANTS))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("--all-tenants");
    }

    @Test
    void shouldRejectATenantTheClusterHasNoConfigurationFor() {
      // given
      final var arguments = arguments();
      arguments.setTenantId("nosuchtenant");

      // when / then
      assertThatThrownBy(() -> arguments.selectionPerPhysicalTenant(TWO_TENANTS))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("nosuchtenant")
          .hasMessageContaining("not configured");
    }
  }

  @Nested
  class Selection {

    @Test
    void shouldApplyTheClusterWideBackupIdsToEveryTenant() {
      // given
      final var arguments = arguments();
      arguments.setAllTenants("true");
      arguments.setBackupId(List.of(27L));

      // when
      final var selection = arguments.selectionPerPhysicalTenant(TWO_TENANTS);

      // then
      assertThat(selection)
          .containsAllEntriesOf(
              Map.of(
                  "default", RestoreSelection.ofBackupIds(List.of(27L)),
                  "tenanta", RestoreSelection.ofBackupIds(List.of(27L))));
    }

    @Test
    void shouldApplyTheClusterWideTimeRangeToEveryTenant() {
      // given
      final var from = Instant.parse("2026-01-01T10:00:00Z");
      final var to = Instant.parse("2026-01-01T12:00:00Z");
      final var arguments = arguments();
      arguments.setAllTenants("true");
      arguments.setFrom(from);
      arguments.setTo(to);

      // when
      final var selection = arguments.selectionPerPhysicalTenant(TWO_TENANTS);

      // then — each tenant resolves its own restore point in that window later; the window itself
      // is shared
      assertThat(selection.values())
          .allSatisfy(value -> assertThat(value).isEqualTo(RestoreSelection.ofTimeRange(from, to)));
    }

    @Test
    void shouldLetAnOverrideReplaceTheClusterWideSelectionForThatTenantOnly() {
      // given — a cluster-wide window, and one tenant pinned to explicit backups instead
      final var from = Instant.parse("2026-01-01T10:00:00Z");
      final var arguments = arguments();
      arguments.setAllTenants("true");
      arguments.setFrom(from);
      final var tenantOverride = new TenantOverride();
      tenantOverride.setBackupId(List.of(31L, 32L));
      arguments.setOverride(Map.of("tenanta", tenantOverride));

      // when
      final var selection = arguments.selectionPerPhysicalTenant(TWO_TENANTS);

      // then
      assertThat(selection.get("tenanta"))
          .isEqualTo(RestoreSelection.ofBackupIds(List.of(31L, 32L)));
      assertThat(selection.get("default")).isEqualTo(RestoreSelection.ofTimeRange(from, null));
    }

    @Test
    void shouldSelectNothingWhenNoBackupIsNamed() {
      // given — valid only on RDBMS, where the restore point comes from the exported positions
      final var arguments = arguments();

      // when
      final var selection = arguments.selectionPerPhysicalTenant(ONE_TENANT);

      // then
      assertThat(selection.get("default")).isEqualTo(RestoreSelection.latest());
    }

    @Test
    void shouldRejectNamingBothBackupIdsAndATimeRange() {
      // given
      final var arguments = arguments();
      arguments.setBackupId(List.of(27L));
      arguments.setFrom(Instant.parse("2026-01-01T10:00:00Z"));

      // when / then — they select different things; silently preferring one would restore to a
      // checkpoint the operator did not ask for
      assertThatThrownBy(() -> arguments.selectionPerPhysicalTenant(ONE_TENANT))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("either backup ids or a time range");
    }
  }

  /**
   * The restore identity. The pre- and post-restore actions coordinate nodes under it, so two runs
   * that restore different things must not share one.
   */
  @Nested
  class RestoreId {

    @Test
    void shouldDistinguishTenantSelectionsWhenNoBackupIsNamed() {
      // given — an RDBMS restore, whose restore point comes from the exported positions, so
      // nothing in the arguments names a backup
      final var justDefault = Map.of("default", RestoreSelection.latest());
      final var justTenantA = Map.of("tenanta", RestoreSelection.latest());
      final var both =
          Map.of("default", RestoreSelection.latest(), "tenanta", RestoreSelection.latest());

      // when / then — sharing an id would let the S3 coordination treat a completed restore of one
      // tenant as covering a later run for another: that run is skipped, and the directories
      // already on disk let the post-restore check pass while the restore never happened
      assertThat(RestoreApp.getRestoreId(justDefault))
          .isNotEqualTo(RestoreApp.getRestoreId(justTenantA))
          .isNotEqualTo(RestoreApp.getRestoreId(both));
      assertThat(RestoreApp.getRestoreId(justTenantA)).isNotEqualTo(RestoreApp.getRestoreId(both));
    }

    @Test
    void shouldDistinguishBackupSelectionsForTheSameTenant() {
      // given
      final var from27 = Map.of("default", RestoreSelection.ofBackupIds(List.of(27L)));
      final var from31 = Map.of("default", RestoreSelection.ofBackupIds(List.of(31L)));

      // when / then
      assertThat(RestoreApp.getRestoreId(from27)).isNotEqualTo(RestoreApp.getRestoreId(from31));
    }

    @Test
    void shouldBeStableForTheSameSelection() {
      // given — every node computes this independently from the same arguments and must agree
      final var selection = Map.of("default", RestoreSelection.ofBackupIds(List.of(27L)));

      // when / then
      assertThat(RestoreApp.getRestoreId(selection))
          .isEqualTo(RestoreApp.getRestoreId(Map.copyOf(selection)));
    }
  }

  /**
   * What a run is allowed to do to data already in the data directory. Naming a tenant is the only
   * case where the application deletes anything of its own accord.
   */
  @Nested
  class TargetData {

    @Test
    void shouldRequireAnEmptyDirectoryForTheDefaultTenant() {
      // given — no --tenant-id, which is every pre-existing invocation
      final var arguments = arguments();

      // when / then — the long-standing refusal, unchanged
      assertThat(arguments.targetDataPolicy()).isEqualTo(TargetDataPolicy.REQUIRE_EMPTY);
    }

    @Test
    void shouldRequireAnEmptyDirectoryForEveryTenant() {
      // given
      final var arguments = arguments();
      arguments.setAllTenants("true");

      // when / then — restoring the whole cluster is the recovery of a node meant to start from
      // nothing, so there is nothing it should be replacing
      assertThat(arguments.targetDataPolicy()).isEqualTo(TargetDataPolicy.REQUIRE_EMPTY);
    }

    @Test
    void shouldReplaceOnlyTheNamedTenantsData() {
      // given
      final var arguments = arguments();
      arguments.setTenantId("tenanta");

      // when / then — naming a tenant is asking to replace it on a node whose other tenants are
      // live, so an empty-directory requirement could never be satisfied
      assertThat(arguments.targetDataPolicy()).isEqualTo(TargetDataPolicy.REPLACE_SELECTED);
    }
  }
}
