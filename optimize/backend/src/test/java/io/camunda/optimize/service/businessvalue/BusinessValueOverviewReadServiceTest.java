/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.AutomationRateBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.CycleTimeBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.tenant.TenantService;
import io.camunda.optimize.service.util.configuration.BusinessValueConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BusinessValueOverviewReadServiceTest {

  private static final String USER = "user-1";
  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";
  private static final long REFRESH_INTERVAL_SECONDS = 86_400L;
  private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

  private BusinessValueOverviewRepository overviewRepository;
  private TenantService tenantService;
  private BusinessValueOverviewComputeService computeService;
  private BusinessValueOverviewReadService readService;

  @BeforeEach
  void setUp() {
    overviewRepository = mock(BusinessValueOverviewRepository.class);
    tenantService = mock(TenantService.class);
    computeService = mock(BusinessValueOverviewComputeService.class);
    final ConfigurationService configurationService = mock(ConfigurationService.class);
    final BusinessValueConfiguration businessValueConfiguration = new BusinessValueConfiguration();
    businessValueConfiguration.setOverviewRefreshInterval(REFRESH_INTERVAL_SECONDS);
    when(configurationService.getBusinessValueConfiguration())
        .thenReturn(businessValueConfiguration);
    when(tenantService.isAuthorizedToSeeTenant(anyString(), anyString())).thenReturn(true);
    readService =
        new BusinessValueOverviewReadService(
            overviewRepository, tenantService, computeService, configurationService);
  }

  @Test
  void shouldReturnEmptyResponseWhenNoRows() {
    // given
    when(overviewRepository.readByRange(MetricRange.THIRTY_DAYS)).thenReturn(List.of());

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then — hasAnyTarget is false; empty coverage/attainment; two zero-filled categories; no
    // off-target entries. Sending a stable shape means the FE can render category donuts even
    // before any target is set.
    assertThat(response.hasAnyTarget()).isFalse();
    assertThat(response.coverage().processesWithTarget()).isZero();
    assertThat(response.coverage().totalProcesses()).isZero();
    assertThat(response.attainment().targetsSet()).isZero();
    assertThat(response.attainment().targetsMet()).isZero();
    assertThat(response.categories()).hasSize(2);
    assertThat(response.categories())
        .extracting(BusinessValueOverviewResponseDto.CategoryDto::kpi)
        .containsExactly("cycleTime", "automationRate");
    assertThat(response.offTarget()).isEmpty();
  }

  @Test
  void shouldReportCoverageAndAttainmentCounts() {
    // given three rows — two with any target, one with a met target
    when(overviewRepository.readByRange(MetricRange.THIRTY_DAYS))
        .thenReturn(
            List.of(
                fresh(row(TENANT_A, "proc-1", cyc(6_000L, 8_000L, true), autoNull(), 1, 1)),
                fresh(row(TENANT_A, "proc-2", cycNull(), aut(60.0, 85, false), 1, 0)),
                fresh(row(TENANT_A, "proc-3", cycNull(), autoNull(), 0, 0))));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.hasAnyTarget()).isTrue();
    assertThat(response.coverage().processesWithTarget()).isEqualTo(2);
    assertThat(response.coverage().totalProcesses()).isEqualTo(3);
    assertThat(response.attainment().targetsSet()).isEqualTo(2);
    assertThat(response.attainment().targetsMet()).isEqualTo(1);
  }

  @Test
  void shouldBuildCategoriesForBothKpisWithZeroSafeArithmetic() {
    // given two rows, one for each KPI, only one met each
    when(overviewRepository.readByRange(MetricRange.THIRTY_DAYS))
        .thenReturn(
            List.of(
                fresh(row(TENANT_A, "proc-cyc-met", cyc(6_000L, 8_000L, true), autoNull(), 1, 1)),
                fresh(row(TENANT_A, "proc-auto-missed", cycNull(), aut(70.0, 90, false), 1, 0))));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then — the "totalApplicable" column reflects the row count because every row is applicable
    // for both KPIs in v1; "processesWithTarget" and "targetsMet" reflect only the non-null side.
    final BusinessValueOverviewResponseDto.CategoryDto cycleTimeCategory =
        response.categories().stream()
            .filter(c -> c.kpi().equals("cycleTime"))
            .findFirst()
            .orElseThrow();
    assertThat(cycleTimeCategory.processesWithTarget()).isEqualTo(1);
    assertThat(cycleTimeCategory.totalApplicable()).isEqualTo(2);
    assertThat(cycleTimeCategory.targetsMet()).isEqualTo(1);

    final BusinessValueOverviewResponseDto.CategoryDto automationRateCategory =
        response.categories().stream()
            .filter(c -> c.kpi().equals("automationRate"))
            .findFirst()
            .orElseThrow();
    assertThat(automationRateCategory.processesWithTarget()).isEqualTo(1);
    assertThat(automationRateCategory.totalApplicable()).isEqualTo(2);
    assertThat(automationRateCategory.targetsMet()).isZero();
  }

  @Test
  void shouldEmitOffTargetOnlyForRowsWithTargetAndNotMet() {
    // given four rows: no target, met, missed, missed with null value (data gap)
    when(overviewRepository.readByRange(MetricRange.THIRTY_DAYS))
        .thenReturn(
            List.of(
                fresh(row(TENANT_A, "proc-no-target", cycNull(), autoNull(), 0, 0)),
                fresh(row(TENANT_A, "proc-met", cyc(6_000L, 8_000L, true), autoNull(), 1, 1)),
                fresh(row(TENANT_A, "proc-missed", cyc(9_000L, 8_000L, false), autoNull(), 1, 0)),
                // met==false but no value available — treat as data gap, skip from off-target
                fresh(
                    row(
                        TENANT_A,
                        "proc-null-value",
                        new CycleTimeBlock(null, 8_000L, false),
                        autoNull(),
                        1,
                        0))));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.offTarget())
        .extracting(BusinessValueOverviewResponseDto.OffTargetEntryDto::processKey)
        .containsExactly("proc-missed");
  }

  @Test
  void shouldSortOffTargetByGapPctDescending() {
    // given three missed rows with different gap magnitudes
    when(overviewRepository.readByRange(MetricRange.THIRTY_DAYS))
        .thenReturn(
            List.of(
                fresh(row(TENANT_A, "proc-25pct", cyc(10_000L, 8_000L, false), autoNull(), 1, 0)),
                fresh(row(TENANT_A, "proc-50pct", cyc(12_000L, 8_000L, false), autoNull(), 1, 0)),
                fresh(row(TENANT_A, "proc-10pct", cyc(8_800L, 8_000L, false), autoNull(), 1, 0))));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.offTarget())
        .extracting(BusinessValueOverviewResponseDto.OffTargetEntryDto::processKey)
        .containsExactly("proc-50pct", "proc-25pct", "proc-10pct");
  }

  @Test
  void shouldComputeGapPctAndDirectionFromVerdictHelper() {
    // given — cycle time value 10, target 8, LOWER_IS_BETTER; automation 70, target 85, HIGHER
    when(overviewRepository.readByRange(MetricRange.THIRTY_DAYS))
        .thenReturn(
            List.of(
                fresh(row(TENANT_A, "cyc", cyc(10L, 8L, false), autoNull(), 1, 0)),
                fresh(row(TENANT_A, "aut", cycNull(), aut(70.0, 85, false), 1, 0))));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then — matches the verdict-cases.json fixture rows for "cycle-time over target" and
    // "automation below target"; keeping this test parametric on the DTO output guards against
    // drift between the Java verdict function and the read-service assembly.
    final BusinessValueOverviewResponseDto.OffTargetEntryDto cycEntry =
        response.offTarget().stream()
            .filter(e -> e.kpi().equals("cycleTime"))
            .findFirst()
            .orElseThrow();
    assertThat(cycEntry.gapPct()).isEqualTo(25.0);
    assertThat(cycEntry.direction()).isEqualTo("over");
    assertThat(cycEntry.displayUnit()).isEqualTo("HOURS");

    final BusinessValueOverviewResponseDto.OffTargetEntryDto autoEntry =
        response.offTarget().stream()
            .filter(e -> e.kpi().equals("automationRate"))
            .findFirst()
            .orElseThrow();
    assertThat(autoEntry.gapPct()).isEqualTo(17.647058823529413);
    assertThat(autoEntry.direction()).isEqualTo("under");
    assertThat(autoEntry.displayUnit()).isEqualTo("PERCENT");
  }

  @Test
  void shouldFireStaleBackstopWhenLastComputedAtOlderThanTwoTimesInterval() {
    // given — the row was computed three intervals ago; threshold is 2×
    final BusinessValueOverviewDto staleRow =
        stamp(
            row(TENANT_A, "proc-stale", cyc(6_000L, 8_000L, true), autoNull(), 1, 1),
            NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS));
    when(overviewRepository.readByRange(MetricRange.THIRTY_DAYS)).thenReturn(List.of(staleRow));

    // when
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then — the backstop fires asynchronously; verify with Awaitility since the runAsync work is
    // scheduled on the ForkJoinPool.
    Awaitility.await()
        .untilAsserted(
            () ->
                verify(computeService)
                    .computeOverviewRows(
                        eq(
                            BusinessValueOverviewScope.definition(
                                staleRow.getTenantId(), staleRow.getProcessDefinitionKey())),
                        eq(List.of(MetricRange.THIRTY_DAYS)),
                        eq(BusinessValueOverviewRefreshMode.SCHEDULER)));
  }

  @Test
  void shouldNotFireBackstopWhenFresh() {
    // given
    when(overviewRepository.readByRange(MetricRange.THIRTY_DAYS))
        .thenReturn(
            List.of(
                fresh(row(TENANT_A, "proc-fresh", cyc(6_000L, 8_000L, true), autoNull(), 1, 1))));

    // when
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then — no backstop invocation. Wait a tick to ensure any (buggy) async fire would have
    // landed.
    Awaitility.await()
        .during(java.time.Duration.ofMillis(150))
        .atMost(java.time.Duration.ofMillis(500))
        .untilAsserted(
            () -> verify(computeService, never()).computeOverviewRows(any(), anyList(), any()));
  }

  @Test
  void shouldFilterOutRowsForTenantsUserIsNotAuthorizedFor() {
    // given
    when(tenantService.isAuthorizedToSeeTenant(USER, TENANT_A)).thenReturn(true);
    when(tenantService.isAuthorizedToSeeTenant(USER, TENANT_B)).thenReturn(false);
    when(overviewRepository.readByRange(MetricRange.THIRTY_DAYS))
        .thenReturn(
            List.of(
                fresh(row(TENANT_A, "proc-a", cyc(6_000L, 8_000L, true), autoNull(), 1, 1)),
                fresh(row(TENANT_B, "proc-b", cyc(9_000L, 8_000L, false), autoNull(), 1, 0))));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.coverage().totalProcesses()).isEqualTo(1);
    assertThat(response.offTarget()).isEmpty();
  }

  @Test
  void shouldPassCorrectMetricRangeThroughToBackstop() {
    // given — three ranges, only one is stale
    final BusinessValueOverviewDto staleRow =
        stamp(
            row(TENANT_A, "proc-x", cyc(6_000L, 8_000L, true), autoNull(), 1, 1),
            NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS));
    when(overviewRepository.readByRange(MetricRange.SIX_MONTHS)).thenReturn(List.of(staleRow));

    // when
    readService.getOverview(USER, MetricRange.SIX_MONTHS);

    // then — backstop invoked with 6m range, not any other
    final ArgumentCaptor<List<MetricRange>> ranges = argCaptor();
    Awaitility.await()
        .untilAsserted(
            () -> verify(computeService).computeOverviewRows(any(), ranges.capture(), any()));
    assertThat(ranges.getValue()).containsExactly(MetricRange.SIX_MONTHS);
  }

  private static BusinessValueOverviewDto row(
      final String tenantId,
      final String processKey,
      final CycleTimeBlock cycleTime,
      final AutomationRateBlock automationRate,
      final int targetsSet,
      final int targetsMet) {
    return new BusinessValueOverviewDto(
        tenantId,
        processKey,
        processKey,
        MetricRange.THIRTY_DAYS,
        null,
        cycleTime,
        automationRate,
        targetsSet > 0,
        targetsSet,
        targetsMet);
  }

  private static BusinessValueOverviewDto fresh(final BusinessValueOverviewDto row) {
    return stamp(row, NOW);
  }

  private static BusinessValueOverviewDto stamp(
      final BusinessValueOverviewDto row, final OffsetDateTime lastComputedAt) {
    row.setLastComputedAt(lastComputedAt);
    return row;
  }

  private static CycleTimeBlock cyc(final Long value, final Long target, final Boolean met) {
    return new CycleTimeBlock(value, target, met);
  }

  private static CycleTimeBlock cycNull() {
    return new CycleTimeBlock(null, null, null);
  }

  private static AutomationRateBlock aut(
      final Double value, final Integer target, final Boolean met) {
    return new AutomationRateBlock(value, target, met);
  }

  private static AutomationRateBlock autoNull() {
    return new AutomationRateBlock(null, null, null);
  }

  @SuppressWarnings("unchecked")
  private static <T> ArgumentCaptor<T> argCaptor() {
    return (ArgumentCaptor<T>) ArgumentCaptor.forClass(List.class);
  }
}
