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
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
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
 * Verifies the scheduler compute path end to end: after persisting one process definition (with the
 * "not defined" tenant surfaced as {@code null} alongside a real tenant) and writing a target,
 * invoking the compute service produces one overview row per range preset for the real-tenant
 * definition — the null-tenant bucket is skipped rather than crashing the sweep, and the row's
 * target block matches the caller-scoped tenant instead of leaking across tenants.
 */
class BusinessValueOverviewComputeIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String PROCESS_KEY = "invoice-automation";
  private static final String DEFAULT_TENANT = ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
  private static final Long CYCLE_TARGET_MILLIS = 28_800_000L;
  private static final Integer AUTOMATION_TARGET_PCT = 85;

  private BusinessValueTargetWriter targetWriter;
  private BusinessValueOverviewComputeService computeService;
  private BusinessValueOverviewRepository overviewRepository;

  @BeforeEach
  void setUp() {
    targetWriter = embeddedOptimizeExtension.getBean(BusinessValueTargetWriter.class);
    computeService = embeddedOptimizeExtension.getBean(BusinessValueOverviewComputeService.class);
    overviewRepository = embeddedOptimizeExtension.getBean(BusinessValueOverviewRepository.class);
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
