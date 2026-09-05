/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import static io.camunda.optimize.BusinessValueInstanceFixtures.bvdInstanceWithDuration;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetUpsertRequestDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.CycleTimeTargetDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import io.camunda.optimize.service.dashboard.BusinessValueDashboardService;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.db.writer.BusinessValueTargetWriter;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the scheduler compute path end to end, across two concerns.
 *
 * <p><strong>Row materialization.</strong> After persisting one process definition (with the "not
 * defined" tenant surfaced as {@code null} alongside a real tenant) and writing a target, invoking
 * the compute service produces one overview row per range preset for the real-tenant definition —
 * the null-tenant bucket is skipped rather than crashing the sweep, and the row's target block
 * matches the caller-scoped tenant instead of leaking across tenants.
 *
 * <p><strong>Values produced by the fanned-in evaluation.</strong> Per-tenant isolation on a shared
 * process key, distinct values for definitions resolved by a single evaluation, and a
 * deployed-but-never-run definition neither disturbing its neighbours nor losing its row.
 */
class BusinessValueOverviewComputeIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String PROCESS_KEY = "invoice-automation";
  private static final String DEFAULT_TENANT = ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
  private static final Long CYCLE_TARGET_MILLIS = 28_800_000L;
  private static final Integer AUTOMATION_TARGET_PCT = 85;

  private BusinessValueTargetWriter targetWriter;
  private BusinessValueOverviewComputeService computeService;
  private BusinessValueOverviewRepository overviewRepository;
  private BusinessValueTargetService targetService;

  @BeforeEach
  void setUp() {
    targetWriter = embeddedOptimizeExtension.getBean(BusinessValueTargetWriter.class);
    computeService = embeddedOptimizeExtension.getBean(BusinessValueOverviewComputeService.class);
    overviewRepository = embeddedOptimizeExtension.getBean(BusinessValueOverviewRepository.class);
    targetService = embeddedOptimizeExtension.getBean(BusinessValueTargetService.class);
    // AbstractBrokerlessZeebeCCSMIT.cleanupOptimizeData() wipes Optimize data between tests, so
    // the ApplicationReadyEvent-driven seed is only visible to the first test. Reseed here so the
    // compute service can look up the two per-process seeded reports on every run.
    embeddedOptimizeExtension.getBean(BusinessValueDashboardService.class).reconcile();
  }

  @Test
  void shouldMaterializeRowsPerRangeForImportedDefinitionsAndSkipNullTenants() {
    // given one definition on the default tenant and a target for that pair
    persistProcessDefinitions(
        List.of(
            ProcessDefinitionOptimizeDto.builder()
                .id(PROCESS_KEY + "-1")
                .key(PROCESS_KEY)
                .version("1")
                .name("Invoice Automation")
                .dataSource(new ZeebeDataSourceDto("test", 1))
                .tenantId(DEFAULT_TENANT)
                .bpmn20Xml("<definitions/>")
                .build()));
    targetWriter.upsertTarget(
        new BusinessValueTargetDto(
            PROCESS_KEY,
            DEFAULT_TENANT,
            CYCLE_TARGET_MILLIS,
            TargetValueUnit.HOURS,
            AUTOMATION_TARGET_PCT,
            OffsetDateTime.parse("2026-08-05T04:00:15Z"),
            "sherrin@camunda.com"));

    // when the scheduler compute path runs across all 4 range presets
    computeService.computeOverviewRows(
        List.of(
            MetricRange.SEVEN_DAYS,
            MetricRange.THIRTY_DAYS,
            MetricRange.THREE_MONTHS,
            MetricRange.SIX_MONTHS));

    // then one row exists per range for the (default tenant, key) pair with target propagated
    for (final MetricRange range : MetricRange.values()) {
      final Optional<BusinessValueOverviewDto> row =
          overviewRepository.getByKey(DEFAULT_TENANT, PROCESS_KEY, range);
      assertThat(row)
          .as("row for range %s", range)
          .isPresent()
          .hasValueSatisfying(
              r -> {
                assertThat(r.getTenantId()).isEqualTo(DEFAULT_TENANT);
                assertThat(r.getProcessDefinitionKey()).isEqualTo(PROCESS_KEY);
                assertThat(r.getMetricRange()).isEqualTo(range);
                assertThat(r.getCycleTime().getTarget()).isEqualTo(CYCLE_TARGET_MILLIS);
                assertThat(r.getAutomationRate().getTarget()).isEqualTo(AUTOMATION_TARGET_PCT);
                assertThat(r.isHasAnyTarget()).isTrue();
                assertThat(r.getTargetsSet()).isEqualTo(2);
              });
    }
  }

  @Test
  void shouldNotLeakTargetsAcrossTenantsOnSameProcessKey() {
    // given the same process key exists on two tenants but only one carries a target
    final String otherTenant = "tenant-b";
    persistProcessDefinitions(
        List.of(
            ProcessDefinitionOptimizeDto.builder()
                .id(PROCESS_KEY + "-1")
                .key(PROCESS_KEY)
                .version("1")
                .name("Invoice Automation")
                .dataSource(new ZeebeDataSourceDto("test", 1))
                .tenantId(DEFAULT_TENANT)
                .bpmn20Xml("<definitions/>")
                .build(),
            ProcessDefinitionOptimizeDto.builder()
                .id(PROCESS_KEY + "-2")
                .key(PROCESS_KEY)
                .version("1")
                .name("Invoice Automation")
                .dataSource(new ZeebeDataSourceDto("test", 1))
                .tenantId(otherTenant)
                .bpmn20Xml("<definitions/>")
                .build()));
    targetWriter.upsertTarget(
        new BusinessValueTargetDto(
            PROCESS_KEY,
            DEFAULT_TENANT,
            CYCLE_TARGET_MILLIS,
            TargetValueUnit.HOURS,
            AUTOMATION_TARGET_PCT,
            OffsetDateTime.parse("2026-08-05T04:00:15Z"),
            "sherrin@camunda.com"));

    // when compute runs for the 7d preset
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the default-tenant row carries the target, the other-tenant row does not
    assertThat(overviewRepository.getByKey(DEFAULT_TENANT, PROCESS_KEY, MetricRange.SEVEN_DAYS))
        .isPresent()
        .hasValueSatisfying(
            r -> {
              assertThat(r.getCycleTime().getTarget()).isEqualTo(CYCLE_TARGET_MILLIS);
              assertThat(r.getAutomationRate().getTarget()).isEqualTo(AUTOMATION_TARGET_PCT);
              assertThat(r.getTargetsSet()).isEqualTo(2);
            });
    assertThat(overviewRepository.getByKey(otherTenant, PROCESS_KEY, MetricRange.SEVEN_DAYS))
        .isPresent()
        .hasValueSatisfying(
            r -> {
              assertThat(r.getCycleTime().getTarget()).isNull();
              assertThat(r.getAutomationRate().getTarget()).isNull();
              assertThat(r.getTargetsSet()).isZero();
            });
  }

  /**
   * Guards per-tenant scoping of the computed values, which nothing else does — {@link
   * #shouldNotLeakTargetsAcrossTenantsOnSameProcessKey} asserts on targets, and those come from the
   * target index rather than from report evaluation, so it stays green however evaluation behaves.
   *
   * <p>Note the failure mode if the {@code businessValueReport} flag stopped being cleared: the
   * evaluation handler would resolve definitions for a user, this sweep has none, and the sweep
   * would fail with {@code ForbiddenException("userId is null")} — so this test would go red on an
   * exception rather than on a blended average. Either way the pinned tenant scope is gone.
   */
  @Test
  void shouldComputeTenantScopedValuesForTheSameProcessKey() {
    // given the same process key on two tenants with clearly separated cycle times. Unique per-test
    // identifiers keep this independent of the other tests that use PROCESS_KEY.
    final String processKey = PROCESS_KEY + "-tenant-scoped";
    final String tenantA = DEFAULT_TENANT;
    final String otherTenant = "tenant-scoped-b";
    persistProcessInstances(
        List.of(
            bvdInstanceWithDuration(processKey, 1_000L).build(),
            bvdInstanceWithDuration(processKey, 1_000L).build()));
    persistProcessInstances(
        List.of(
            bvdInstanceWithDuration(processKey, 9_000L).tenantId(otherTenant).build(),
            bvdInstanceWithDuration(processKey, 9_000L).tenantId(otherTenant).build()));
    // persistProcessInstances derives its definitions on the default tenant only, so the
    // other tenant's definition has to be written explicitly for it to enter the sweep
    persistProcessDefinitions(List.of(bvdDefinition(processKey, otherTenant)));
    givenTargetFor(tenantA, processKey);
    givenTargetFor(otherTenant, processKey);

    // when compute runs for the 7d preset
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then each tenant's row carries its own average, not the 5_000 blend of the two
    assertThat(overviewRepository.getByKey(tenantA, processKey, MetricRange.SEVEN_DAYS))
        .isPresent()
        .hasValueSatisfying(r -> assertThat(r.getCycleTime().getValue()).isEqualTo(1_000L));
    assertThat(overviewRepository.getByKey(otherTenant, processKey, MetricRange.SEVEN_DAYS))
        .isPresent()
        .hasValueSatisfying(r -> assertThat(r.getCycleTime().getValue()).isEqualTo(9_000L));
  }

  @Test
  void shouldComputeDistinctValuesForDefinitionsSharingOneEvaluation() {
    // given three definitions on one tenant, all resolved by a single fanned-in evaluation
    final String slow = PROCESS_KEY + "-slow";
    final String mid = PROCESS_KEY + "-mid";
    final String fast = PROCESS_KEY + "-fast";
    persistProcessInstances(
        List.of(
            bvdInstanceWithDuration(slow, 10_000L).build(),
            bvdInstanceWithDuration(mid, 5_000L).build(),
            bvdInstanceWithDuration(fast, 1_000L).build()));
    givenTargetFor(DEFAULT_TENANT, slow);
    givenTargetFor(DEFAULT_TENANT, mid);
    givenTargetFor(DEFAULT_TENANT, fast);

    // when compute runs for the 7d preset
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then every definition takes its own value out of the shared result map
    assertThat(overviewRepository.getByKey(DEFAULT_TENANT, slow, MetricRange.SEVEN_DAYS))
        .isPresent()
        .hasValueSatisfying(r -> assertThat(r.getCycleTime().getValue()).isEqualTo(10_000L));
    assertThat(overviewRepository.getByKey(DEFAULT_TENANT, mid, MetricRange.SEVEN_DAYS))
        .isPresent()
        .hasValueSatisfying(r -> assertThat(r.getCycleTime().getValue()).isEqualTo(5_000L));
    assertThat(overviewRepository.getByKey(DEFAULT_TENANT, fast, MetricRange.SEVEN_DAYS))
        .isPresent()
        .hasValueSatisfying(r -> assertThat(r.getCycleTime().getValue()).isEqualTo(1_000L));
  }

  @Test
  void shouldStillComputeNeighboursWhenADefinitionHasNeverRun() {
    // given one definition with instances and one that was deployed but never ran, so it has no
    // process instance index at all
    final String neverRun = PROCESS_KEY + "-never-run";
    persistProcessInstances(List.of(bvdInstanceWithDuration(PROCESS_KEY, 4_000L).build()));
    persistProcessDefinitions(List.of(bvdDefinition(neverRun, DEFAULT_TENANT)));
    // Both are targeted on purpose. The point of this test is that a definition with no instance
    // index does not disturb its neighbour's evaluation, so it has to reach the evaluation set —
    // leaving it untargeted would exclude it for an unrelated reason and the missing-index path
    // would go untested.
    givenTargetFor(DEFAULT_TENANT, PROCESS_KEY);
    givenTargetFor(DEFAULT_TENANT, neverRun);

    // when compute runs for the 7d preset
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the definition with data is unaffected. Without the pre-filter the missing index would
    // fail the search and the interpreter would retry against the instance multi alias — correct
    // results, but every instance index in the cluster opened. The never-run definition still gets
    // a
    // row, with no value.
    assertThat(overviewRepository.getByKey(DEFAULT_TENANT, PROCESS_KEY, MetricRange.SEVEN_DAYS))
        .isPresent()
        .hasValueSatisfying(r -> assertThat(r.getCycleTime().getValue()).isEqualTo(4_000L));
    assertThat(overviewRepository.getByKey(DEFAULT_TENANT, neverRun, MetricRange.SEVEN_DAYS))
        .isPresent()
        .hasValueSatisfying(r -> assertThat(r.getCycleTime().getValue()).isNull());
  }

  /**
   * The bug this path exists for: a target saved between sweeps used to sit in the target index
   * while the overview row kept the target it was last computed with, so L0 showed the old verdict
   * for a full refresh interval. Deliberately runs no sweep between the save and the read — a test
   * that swept in between would pass on the broken behaviour too.
   */
  @Test
  void shouldReflectATargetOnTheOverviewRowsWithoutWaitingForASweep() {
    // given a definition the sweep has measured, with no target yet
    persistProcessDefinitions(List.of(bvdDefinition(PROCESS_KEY, DEFAULT_TENANT)));
    computeService.computeOverviewRows(List.of(MetricRange.values()));
    assertThat(overviewRepository.getByKey(DEFAULT_TENANT, PROCESS_KEY, MetricRange.SEVEN_DAYS))
        .isPresent()
        .hasValueSatisfying(r -> assertThat(r.isHasAnyTarget()).isFalse());

    // when a target is saved through the service, and no sweep runs afterwards
    targetService.upsertTarget(
        "sherrin@camunda.com",
        DEFAULT_TENANT,
        PROCESS_KEY,
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(8L, TargetValueUnit.HOURS, null), AUTOMATION_TARGET_PCT));

    // then every range preset already carries the target and its verdict
    for (final MetricRange range : MetricRange.values()) {
      assertThat(overviewRepository.getByKey(DEFAULT_TENANT, PROCESS_KEY, range))
          .as("row for range %s", range)
          .isPresent()
          .hasValueSatisfying(
              r -> {
                assertThat(r.getCycleTime().getTarget()).isEqualTo(CYCLE_TARGET_MILLIS);
                assertThat(r.getAutomationRate().getTarget()).isEqualTo(AUTOMATION_TARGET_PCT);
                assertThat(r.isHasAnyTarget()).isTrue();
                assertThat(r.getTargetsSet()).isEqualTo(2);
              });
    }
  }

  /**
   * A definition imported since the last sweep has no rows at all, so there is nothing to update —
   * measuring on save is what creates them.
   */
  @Test
  void shouldCreateRowsWhenATargetIsSetOnADefinitionTheSweepHasNeverSeen() {
    // given a definition that exists but has never been swept
    final String freshKey = "fresh-import-" + System.nanoTime();
    persistProcessDefinitions(List.of(bvdDefinition(freshKey, DEFAULT_TENANT)));
    assertThat(overviewRepository.getByKey(DEFAULT_TENANT, freshKey, MetricRange.SEVEN_DAYS))
        .isEmpty();

    // when a target is saved
    targetService.upsertTarget(
        "sherrin@camunda.com",
        DEFAULT_TENANT,
        freshKey,
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(8L, TargetValueUnit.HOURS, null), AUTOMATION_TARGET_PCT));

    // then the rows are created with the target on them
    for (final MetricRange range : MetricRange.values()) {
      assertThat(overviewRepository.getByKey(DEFAULT_TENANT, freshKey, range))
          .as("row for range %s", range)
          .isPresent()
          .hasValueSatisfying(r -> assertThat(r.isHasAnyTarget()).isTrue());
    }
  }

  /**
   * Gives a definition a target, which is what puts it into the sweep's evaluation set — only
   * targeted definitions are measured. Tests asserting on measured values have to call this for
   * every definition whose value they check.
   */
  private void givenTargetFor(final String tenantId, final String processDefinitionKey) {
    targetWriter.upsertTarget(
        new BusinessValueTargetDto(
            processDefinitionKey,
            tenantId,
            CYCLE_TARGET_MILLIS,
            TargetValueUnit.HOURS,
            AUTOMATION_TARGET_PCT,
            OffsetDateTime.parse("2026-08-05T04:00:15Z"),
            "sherrin@camunda.com"));
  }

  private static ProcessDefinitionOptimizeDto bvdDefinition(
      final String key, final String tenantId) {
    return ProcessDefinitionOptimizeDto.builder()
        .id(key + "-" + tenantId)
        .key(key)
        .version("1")
        .name(key)
        .dataSource(new ZeebeDataSourceDto("test", 1))
        .tenantId(tenantId)
        .bpmn20Xml("<definitions/>")
        .build();
  }

  @Test
  void shouldSkipWriteWhenNoDefinitionsExist() {
    // given no imported process definitions and no targets

    // when compute runs across all 4 ranges
    computeService.computeOverviewRows(
        List.of(
            MetricRange.SEVEN_DAYS,
            MetricRange.THIRTY_DAYS,
            MetricRange.THREE_MONTHS,
            MetricRange.SIX_MONTHS));

    // then no rows are written for our process key
    for (final MetricRange range : MetricRange.values()) {
      assertThat(overviewRepository.getByKey(DEFAULT_TENANT, PROCESS_KEY, range)).isEmpty();
    }
  }
}
