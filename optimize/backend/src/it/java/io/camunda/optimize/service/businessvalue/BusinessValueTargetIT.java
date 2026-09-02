/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import io.camunda.optimize.service.db.repository.BusinessValueTargetRepository;
import io.camunda.optimize.service.db.writer.BusinessValueTargetWriter;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@code business-value-target} index round-trips a target document and enforces
 * tenant isolation at the doc-id level. Runs against whichever backend the IT suite is configured
 * with (Elasticsearch or OpenSearch).
 */
class BusinessValueTargetIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String PROCESS_KEY = "invoice-automation";
  private static final String DEFAULT_TENANT = ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
  private static final String OTHER_TENANT = "tenant-b";

  private BusinessValueTargetWriter writer;
  private BusinessValueTargetRepository repository;

  @BeforeEach
  void setUp() {
    writer = embeddedOptimizeExtension.getBean(BusinessValueTargetWriter.class);
    repository = embeddedOptimizeExtension.getBean(BusinessValueTargetRepository.class);
  }

  @Test
  void shouldRoundTripUpsertAndRead() {
    // given
    final BusinessValueTargetDto target = target(PROCESS_KEY, DEFAULT_TENANT, 28_800_000L, 85);

    // when
    writer.upsertTarget(target);

    // then
    final Optional<BusinessValueTargetDto> read = repository.getByKey(DEFAULT_TENANT, PROCESS_KEY);
    assertThat(read).contains(target);
  }

  /**
   * {@code readByTenants} backs the overview read path, so its terms filter is a tenant-isolation
   * boundary rather than a convenience. Exercised against the real store because the filter is
   * built per backend — a unit test can only reach the empty-collection short circuit, and would
   * pass while either engine's query was wrong.
   */
  @Test
  void shouldReadOnlyTheRequestedTenantsTargets() {
    // given the same process key targeted on two tenants
    writer.upsertTarget(target(PROCESS_KEY, DEFAULT_TENANT, 28_800_000L, 85));
    writer.upsertTarget(target(PROCESS_KEY, OTHER_TENANT, 7_200_000L, 40));

    // when reading one tenant
    final List<BusinessValueTargetDto> defaultTenantTargets =
        repository.readByTenants(List.of(DEFAULT_TENANT));

    // then only that tenant's target comes back, with its own values
    assertThat(defaultTenantTargets)
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.getTenantId()).isEqualTo(DEFAULT_TENANT);
              assertThat(t.getCycleTimeTargetMillis()).isEqualTo(28_800_000L);
            });

    // and asking for both returns both, so the filter is narrowing rather than the fixture being
    // half-written
    assertThat(repository.readByTenants(List.of(DEFAULT_TENANT, OTHER_TENANT)))
        .hasSize(2)
        .extracting(BusinessValueTargetDto::getTenantId)
        .containsExactlyInAnyOrder(DEFAULT_TENANT, OTHER_TENANT);
  }

  /** A caller authorized for no tenants must see nothing, not everything. */
  @Test
  void shouldReadNothingForAnEmptyTenantList() {
    // given targets exist
    writer.upsertTarget(target(PROCESS_KEY, DEFAULT_TENANT, 28_800_000L, 85));

    // when the caller is authorized for no tenants
    // then the read is empty rather than falling through to an unfiltered scan
    assertThat(repository.readByTenants(List.of())).isEmpty();
  }

  @Test
  void shouldOverwriteExistingTargetOnSameTenantAndKey() {
    // given
    writer.upsertTarget(target(PROCESS_KEY, DEFAULT_TENANT, 28_800_000L, 85));

    // when
    final BusinessValueTargetDto updated = target(PROCESS_KEY, DEFAULT_TENANT, 14_400_000L, 90);
    writer.upsertTarget(updated);

    // then
    assertThat(repository.getByKey(DEFAULT_TENANT, PROCESS_KEY)).contains(updated);
  }

  @Test
  void shouldIsolateTargetsAcrossTenants() {
    // given two tenants set different targets on the same processDefinitionKey
    final BusinessValueTargetDto defaultTenantTarget =
        target(PROCESS_KEY, DEFAULT_TENANT, 28_800_000L, 85);
    final BusinessValueTargetDto otherTenantTarget =
        target(PROCESS_KEY, OTHER_TENANT, 3_600_000L, 50);

    // when
    writer.upsertTarget(defaultTenantTarget);
    writer.upsertTarget(otherTenantTarget);

    // then neither overwrites the other
    assertThat(repository.getByKey(DEFAULT_TENANT, PROCESS_KEY)).contains(defaultTenantTarget);
    assertThat(repository.getByKey(OTHER_TENANT, PROCESS_KEY)).contains(otherTenantTarget);
  }

  @Test
  void shouldReturnEmptyForMissingKey() {
    assertThat(repository.getByKey(DEFAULT_TENANT, "does-not-exist")).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenTenantMismatches() {
    // given
    writer.upsertTarget(target(PROCESS_KEY, DEFAULT_TENANT, 28_800_000L, 85));

    // when reading with a tenant that never wrote
    // then no row is returned
    assertThat(repository.getByKey(OTHER_TENANT, PROCESS_KEY)).isEmpty();
  }

  @Test
  void shouldReturnAllRowsAcrossTenantsOnScanAll() {
    // given
    final BusinessValueTargetDto a = target(PROCESS_KEY, DEFAULT_TENANT, 28_800_000L, 85);
    final BusinessValueTargetDto b = target(PROCESS_KEY, OTHER_TENANT, 3_600_000L, 50);
    final BusinessValueTargetDto c = target("hr-onboarding", DEFAULT_TENANT, null, 70);
    writer.upsertTarget(a);
    writer.upsertTarget(b);
    writer.upsertTarget(c);

    // when
    final List<BusinessValueTargetDto> all = repository.scanAll();

    // then
    assertThat(all).containsExactlyInAnyOrder(a, b, c);
  }

  private BusinessValueTargetDto target(
      final String processDefinitionKey,
      final String tenantId,
      final Long cycleTimeMillis,
      final Integer automationRatePct) {
    return new BusinessValueTargetDto(
        processDefinitionKey,
        tenantId,
        cycleTimeMillis,
        cycleTimeMillis == null ? null : TargetValueUnit.HOURS,
        automationRatePct,
        OffsetDateTime.parse("2026-08-05T10:15:00Z"),
        "sherrin@camunda.com");
  }
}
