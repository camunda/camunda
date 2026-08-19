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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.AutomationRateBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.CycleTimeBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.tenant.TenantService;
import io.camunda.optimize.service.util.configuration.BusinessValueConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BusinessValueOverviewReadServiceTest {

  private static final String USER = "user-1";
  private static final String TENANT_A = "tenant-a";
  private static final long REFRESH_INTERVAL_SECONDS = 86_400L;
  private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

  private BusinessValueOverviewRepository overviewRepository;
  private TenantService tenantService;
  private DefinitionService definitionService;
  private BusinessValueOverviewComputeService computeService;
  private BusinessValueOverviewReadService readService;

  @BeforeEach
  void setUp() {
    overviewRepository = mock(BusinessValueOverviewRepository.class);
    tenantService = mock(TenantService.class);
    definitionService = mock(DefinitionService.class);
    computeService = mock(BusinessValueOverviewComputeService.class);
    final ConfigurationService configurationService = mock(ConfigurationService.class);
    final BusinessValueConfiguration businessValueConfiguration = new BusinessValueConfiguration();
    businessValueConfiguration.setOverviewRefreshInterval(REFRESH_INTERVAL_SECONDS);
    when(configurationService.getBusinessValueConfiguration())
        .thenReturn(businessValueConfiguration);
    when(tenantService.getTenantIdsForUser(anyString())).thenReturn(List.of(TENANT_A));
    when(definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS))
        .thenReturn(List.of());
    readService =
        new BusinessValueOverviewReadService(
            overviewRepository,
            tenantService,
            definitionService,
            computeService,
            configurationService);
  }

  @Test
  void shouldPushDownAuthorizedTenantIdsToTheRepository() {
    when(tenantService.getTenantIdsForUser(USER)).thenReturn(List.of(TENANT_A, "tenant-b"));
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any())).thenReturn(List.of());

    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    final ArgumentCaptor<Collection<String>> captor = collectionCaptor();
    verify(overviewRepository).readByRange(eq(MetricRange.THIRTY_DAYS), captor.capture());
    assertThat(captor.getValue()).containsExactlyInAnyOrder(TENANT_A, "tenant-b");
  }

  @Test
  void shouldReturnEmptyResponseWhenNoRows() {
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any())).thenReturn(List.of());

    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

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
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "proc-1", cyc(6_000L, 8_000L, true), autoNull(), 1, 1)),
        fresh(row(TENANT_A, "proc-2", cycNull(), aut(60.0, 85, false), 1, 0)),
        fresh(row(TENANT_A, "proc-3", cycNull(), autoNull(), 0, 0)));

    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    assertThat(response.hasAnyTarget()).isTrue();
    assertThat(response.coverage().processesWithTarget()).isEqualTo(2);
    assertThat(response.coverage().totalProcesses()).isEqualTo(3);
    assertThat(response.attainment().targetsSet()).isEqualTo(2);
    assertThat(response.attainment().targetsMet()).isEqualTo(1);
  }

  @Test
  void shouldBuildCategoriesForBothKpisWithZeroSafeArithmetic() {
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "proc-cyc-met", cyc(6_000L, 8_000L, true), autoNull(), 1, 1)),
        fresh(row(TENANT_A, "proc-auto-missed", cycNull(), aut(70.0, 90, false), 1, 0)));

    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

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
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "proc-no-target", cycNull(), autoNull(), 0, 0)),
        fresh(row(TENANT_A, "proc-met", cyc(6_000L, 8_000L, true), autoNull(), 1, 1)),
        fresh(row(TENANT_A, "proc-missed", cyc(9_000L, 8_000L, false), autoNull(), 1, 0)),
        fresh(
            row(
                TENANT_A,
                "proc-null-value",
                new CycleTimeBlock(null, 8_000L, false),
                autoNull(),
                1,
                0)));

    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    assertThat(response.offTarget())
        .extracting(BusinessValueOverviewResponseDto.OffTargetEntryDto::processKey)
        .containsExactly("proc-missed");
  }

  @Test
  void shouldSortOffTargetByGapPctDescending() {
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "proc-25pct", cyc(10_000L, 8_000L, false), autoNull(), 1, 0)),
        fresh(row(TENANT_A, "proc-50pct", cyc(12_000L, 8_000L, false), autoNull(), 1, 0)),
        fresh(row(TENANT_A, "proc-10pct", cyc(8_800L, 8_000L, false), autoNull(), 1, 0)));

    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    assertThat(response.offTarget())
        .extracting(BusinessValueOverviewResponseDto.OffTargetEntryDto::processKey)
        .containsExactly("proc-50pct", "proc-25pct", "proc-10pct");
  }

  @Test
  void shouldComputeGapPctAndDirectionFromVerdictHelper() {
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "cyc", cyc(10L, 8L, false), autoNull(), 1, 0)),
        fresh(row(TENANT_A, "aut", cycNull(), aut(70.0, 85, false), 1, 0)));

    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

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
  void shouldSkipOffTargetEntryWhenCycleTimeTargetIsZero() {
    // Target = 0 is nonsensical for cycle time (LOWER_IS_BETTER) and would produce Infinity gapPct
    // via division by zero, breaking JSON. Frontend validation is the source of truth; this guard
    // survives if the frontend is bypassed or a legacy target row leaks in.
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "zero-target", cyc(100L, 0L, false), autoNull(), 1, 0)));

    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    assertThat(response.offTarget()).isEmpty();
  }

  @Test
  void shouldSkipOffTargetEntryWhenAutomationRateTargetIsZero() {
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "zero-auto", cycNull(), aut(0.0, 0, false), 1, 0)));

    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    assertThat(response.offTarget()).isEmpty();
  }

  @Test
  void shouldFilterOutRowsForDeletedProcessDefinitions() {
    // Two overview rows exist in the index but only one definition is still imported. The stale
    // row for the deleted definition must not appear in the response and must not trigger the
    // backstop, otherwise the compute service would immediately recreate the row via its
    // synthetic-definition fallback and orphans would never age out.
    final BusinessValueOverviewDto liveRow =
        fresh(row(TENANT_A, "still-here", cyc(6_000L, 8_000L, true), autoNull(), 1, 1));
    final BusinessValueOverviewDto orphanRow =
        stamp(
            row(TENANT_A, "deleted-proc", cyc(9_000L, 8_000L, false), autoNull(), 1, 0),
            NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS));
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any()))
        .thenReturn(List.of(liveRow, orphanRow));
    stubCurrentDefinitions(new DefKey(TENANT_A, "still-here"));

    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    assertThat(response.coverage().totalProcesses()).isEqualTo(1);
    assertThat(response.coverage().processesWithTarget()).isEqualTo(1);
    assertThat(response.attainment().targetsSet()).isEqualTo(1);
    assertThat(response.attainment().targetsMet()).isEqualTo(1);
    assertThat(response.offTarget()).isEmpty();

    Awaitility.await()
        .during(java.time.Duration.ofMillis(150))
        .atMost(java.time.Duration.ofMillis(500))
        .untilAsserted(
            () -> verify(computeService, never()).computeOverviewRows(any(), anyList(), any()));
  }

  @Test
  void shouldReturnEmptyResponseWhenEveryRowIsOrphaned() {
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any()))
        .thenReturn(
            List.of(fresh(row(TENANT_A, "gone-1", cyc(6_000L, 8_000L, true), autoNull(), 1, 1))));
    when(definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS))
        .thenReturn(List.of());

    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    assertThat(response.hasAnyTarget()).isFalse();
    assertThat(response.coverage().totalProcesses()).isZero();
    assertThat(response.offTarget()).isEmpty();
  }

  @Test
  void shouldFireStaleBackstopWhenLastComputedAtOlderThanTwoTimesInterval() {
    final BusinessValueOverviewDto staleRow =
        stamp(
            row(TENANT_A, "proc-stale", cyc(6_000L, 8_000L, true), autoNull(), 1, 1),
            NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS));
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any()))
        .thenReturn(List.of(staleRow));
    stubCurrentDefinitions(new DefKey(TENANT_A, "proc-stale"));

    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

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
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "proc-fresh", cyc(6_000L, 8_000L, true), autoNull(), 1, 1)));

    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    Awaitility.await()
        .during(java.time.Duration.ofMillis(150))
        .atMost(java.time.Duration.ofMillis(500))
        .untilAsserted(
            () -> verify(computeService, never()).computeOverviewRows(any(), anyList(), any()));
  }

  @Test
  void shouldCoalesceConcurrentBackstopsForTheSameStaleRow() {
    final BusinessValueOverviewDto staleRow =
        stamp(
            row(TENANT_A, "proc-hot", cyc(6_000L, 8_000L, true), autoNull(), 1, 1),
            NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS));
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any()))
        .thenReturn(List.of(staleRow));
    stubCurrentDefinitions(new DefKey(TENANT_A, "proc-hot"));

    final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              release.await();
              return null;
            })
        .when(computeService)
        .computeOverviewRows(any(), anyList(), any());

    readService.getOverview(USER, MetricRange.THIRTY_DAYS);
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    Awaitility.await()
        .during(java.time.Duration.ofMillis(200))
        .atMost(java.time.Duration.ofMillis(500))
        .untilAsserted(
            () -> verify(computeService, times(1)).computeOverviewRows(any(), anyList(), any()));

    release.countDown();
  }

  @Test
  void shouldPropagateTheStaleRangeToTheBackstop() {
    final BusinessValueOverviewDto staleRow =
        stamp(
            row(TENANT_A, "proc-x", cyc(6_000L, 8_000L, true), autoNull(), 1, 1),
            NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS));
    when(overviewRepository.readByRange(eq(MetricRange.SIX_MONTHS), any()))
        .thenReturn(List.of(staleRow));
    stubCurrentDefinitions(new DefKey(TENANT_A, "proc-x"));

    readService.getOverview(USER, MetricRange.SIX_MONTHS);

    final ArgumentCaptor<List<MetricRange>> ranges = argCaptor();
    Awaitility.await()
        .untilAsserted(
            () -> verify(computeService).computeOverviewRows(any(), ranges.capture(), any()));
    assertThat(ranges.getValue()).containsExactly(MetricRange.SIX_MONTHS);
  }

  private void seedRows(final MetricRange range, final BusinessValueOverviewDto... rows) {
    when(overviewRepository.readByRange(eq(range), any())).thenReturn(List.of(rows));
    final DefKey[] keys = new DefKey[rows.length];
    for (int i = 0; i < rows.length; i++) {
      keys[i] = new DefKey(rows[i].getTenantId(), rows[i].getProcessDefinitionKey());
    }
    stubCurrentDefinitions(keys);
  }

  private void stubCurrentDefinitions(final DefKey... keys) {
    final Map<String, List<String>> tenantsByKey = new HashMap<>();
    for (final DefKey key : keys) {
      tenantsByKey.computeIfAbsent(key.processKey(), k -> new ArrayList<>()).add(key.tenantId());
    }
    final List<DefinitionWithTenantIdsDto> dtos = new ArrayList<>();
    for (final Map.Entry<String, List<String>> entry : tenantsByKey.entrySet()) {
      dtos.add(
          new DefinitionWithTenantIdsDto(
              entry.getKey(), entry.getKey(), DefinitionType.PROCESS, entry.getValue(), Set.of()));
    }
    when(definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS)).thenReturn(dtos);
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

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Collection<String>> collectionCaptor() {
    return (ArgumentCaptor<Collection<String>>) (Object) ArgumentCaptor.forClass(Collection.class);
  }

  private record DefKey(String tenantId, String processKey) {}
}
