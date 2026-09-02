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
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.db.repository.BusinessValueTargetRepository;
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
  private BusinessValueTargetRepository targetRepository;
  private TenantService tenantService;
  private DefinitionService definitionService;
  private BusinessValueOverviewComputeService computeService;
  private BusinessValueOverviewReadService readService;
  private BusinessValueConfiguration businessValueConfiguration;

  @BeforeEach
  void setUp() {
    overviewRepository = mock(BusinessValueOverviewRepository.class);
    targetRepository = mock(BusinessValueTargetRepository.class);
    when(targetRepository.readByTenants(any())).thenReturn(List.of());
    tenantService = mock(TenantService.class);
    definitionService = mock(DefinitionService.class);
    computeService = mock(BusinessValueOverviewComputeService.class);
    final ConfigurationService configurationService = mock(ConfigurationService.class);
    businessValueConfiguration = new BusinessValueConfiguration();
    businessValueConfiguration.setOverviewRefreshInterval(REFRESH_INTERVAL_SECONDS);
    when(configurationService.getBusinessValueConfiguration())
        .thenReturn(businessValueConfiguration);
    when(tenantService.getTenantIdsForUser(anyString())).thenReturn(List.of(TENANT_A));
    when(definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS))
        .thenReturn(List.of());
    readService =
        new BusinessValueOverviewReadService(
            overviewRepository,
            targetRepository,
            tenantService,
            definitionService,
            computeService,
            configurationService);
  }

  @Test
  void shouldPushDownAuthorizedTenantIdsToTheRepository() {
    // given
    when(tenantService.getTenantIdsForUser(USER)).thenReturn(List.of(TENANT_A, "tenant-b"));
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any())).thenReturn(List.of());

    // when
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    final ArgumentCaptor<Collection<String>> captor = collectionCaptor();
    verify(overviewRepository).readByRange(eq(MetricRange.THIRTY_DAYS), captor.capture());
    assertThat(captor.getValue()).containsExactlyInAnyOrder(TENANT_A, "tenant-b");
  }

  @Test
  void shouldPassAnEmptyListWhenUserIsAuthorizedToNoTenants() {
    // A user with zero authorized tenants is a legitimate state — new customer, tenant assignment
    // pending, role that doesn't grant any tenant access. The read must still short-circuit
    // cleanly: pass an empty collection so the repository's is-empty guard returns immediately
    // without hitting the datastore.
    // given
    when(tenantService.getTenantIdsForUser(USER)).thenReturn(List.of());
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any())).thenReturn(List.of());

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    final ArgumentCaptor<Collection<String>> captor = collectionCaptor();
    verify(overviewRepository).readByRange(eq(MetricRange.THIRTY_DAYS), captor.capture());
    assertThat(captor.getValue()).isEmpty();
    assertThat(response.isHasAnyTarget()).isFalse();
    assertThat(response.getCoverage().getTotalProcesses()).isZero();
  }

  @Test
  void shouldReturnEmptyResponseWhenNoRows() {
    // given
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any())).thenReturn(List.of());

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.isHasAnyTarget()).isFalse();
    assertThat(response.getCoverage().getProcessesWithTarget()).isZero();
    assertThat(response.getCoverage().getTotalProcesses()).isZero();
    assertThat(response.getAttainment().getTargetsSet()).isZero();
    assertThat(response.getAttainment().getTargetsMet()).isZero();
    assertThat(response.getCategories()).hasSize(2);
    assertThat(response.getCategories())
        .extracting(BusinessValueOverviewResponseDto.CategoryDto::getKpi)
        .containsExactly("cycleTime", "automationRate");
    assertThat(response.getOffTarget()).isEmpty();
  }

  @Test
  void shouldReportCoverageAndAttainmentCounts() {
    // given
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "proc-1", cyc(6_000L, 8_000L, true), autoNull(), 1, 1)),
        fresh(row(TENANT_A, "proc-2", cycNull(), aut(60.0, 85, false), 1, 0)),
        fresh(row(TENANT_A, "proc-3", cycNull(), autoNull(), 0, 0)));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.isHasAnyTarget()).isTrue();
    assertThat(response.getCoverage().getProcessesWithTarget()).isEqualTo(2);
    assertThat(response.getCoverage().getTotalProcesses()).isEqualTo(3);
    assertThat(response.getAttainment().getTargetsSet()).isEqualTo(2);
    assertThat(response.getAttainment().getTargetsMet()).isEqualTo(1);
  }

  @Test
  void shouldBuildCategoriesForBothKpisWithZeroSafeArithmetic() {
    // given
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "proc-cyc-met", cyc(6_000L, 8_000L, true), autoNull(), 1, 1)),
        fresh(row(TENANT_A, "proc-auto-missed", cycNull(), aut(70.0, 90, false), 1, 0)));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    final BusinessValueOverviewResponseDto.CategoryDto cycleTimeCategory =
        response.getCategories().stream()
            .filter(c -> c.getKpi().equals("cycleTime"))
            .findFirst()
            .orElseThrow();
    assertThat(cycleTimeCategory.getProcessesWithTarget()).isEqualTo(1);
    assertThat(cycleTimeCategory.getTotalApplicable()).isEqualTo(2);
    assertThat(cycleTimeCategory.getTargetsMet()).isEqualTo(1);

    final BusinessValueOverviewResponseDto.CategoryDto automationRateCategory =
        response.getCategories().stream()
            .filter(c -> c.getKpi().equals("automationRate"))
            .findFirst()
            .orElseThrow();
    assertThat(automationRateCategory.getProcessesWithTarget()).isEqualTo(1);
    assertThat(automationRateCategory.getTotalApplicable()).isEqualTo(2);
    assertThat(automationRateCategory.getTargetsMet()).isZero();
  }

  @Test
  void shouldEmitOffTargetOnlyForRowsWithTargetAndNotMet() {
    // given
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

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.getOffTarget())
        .extracting(BusinessValueOverviewResponseDto.OffTargetEntryDto::getProcessKey)
        .containsExactly("proc-missed");
  }

  @Test
  void shouldSortOffTargetByGapPctDescending() {
    // given
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "proc-25pct", cyc(10_000L, 8_000L, false), autoNull(), 1, 0)),
        fresh(row(TENANT_A, "proc-50pct", cyc(12_000L, 8_000L, false), autoNull(), 1, 0)),
        fresh(row(TENANT_A, "proc-10pct", cyc(8_800L, 8_000L, false), autoNull(), 1, 0)));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.getOffTarget())
        .extracting(BusinessValueOverviewResponseDto.OffTargetEntryDto::getProcessKey)
        .containsExactly("proc-50pct", "proc-25pct", "proc-10pct");
  }

  @Test
  void shouldComputeGapPctAndDirectionFromVerdictHelper() {
    // given
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "cyc", cyc(10L, 8L, false), autoNull(), 1, 0)),
        fresh(row(TENANT_A, "aut", cycNull(), aut(70.0, 85, false), 1, 0)));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    final BusinessValueOverviewResponseDto.OffTargetEntryDto cycEntry =
        response.getOffTarget().stream()
            .filter(e -> e.getKpi().equals("cycleTime"))
            .findFirst()
            .orElseThrow();
    assertThat(cycEntry.getGapPct()).isEqualTo(25.0);
    assertThat(cycEntry.getComparison()).isEqualTo("over");
    assertThat(cycEntry.getDisplayUnit()).isEqualTo("HOURS");

    final BusinessValueOverviewResponseDto.OffTargetEntryDto autoEntry =
        response.getOffTarget().stream()
            .filter(e -> e.getKpi().equals("automationRate"))
            .findFirst()
            .orElseThrow();
    assertThat(autoEntry.getGapPct()).isEqualTo(17.647058823529413);
    assertThat(autoEntry.getComparison()).isEqualTo("under");
    assertThat(autoEntry.getDisplayUnit()).isEqualTo("PERCENT");
  }

  @Test
  void shouldSkipOffTargetEntryWhenCycleTimeTargetIsZero() {
    // Target = 0 is nonsensical for cycle time (LOWER_IS_BETTER) and would produce Infinity gapPct
    // via division by zero, breaking JSON. Frontend validation is the source of truth; this guard
    // survives if the frontend is bypassed or a legacy target row leaks in.
    // given
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "zero-target", cyc(100L, 0L, false), autoNull(), 1, 0)));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.getOffTarget()).isEmpty();
  }

  @Test
  void shouldSkipOffTargetEntryWhenAutomationRateTargetIsZero() {
    // given
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "zero-auto", cycNull(), aut(0.0, 0, false), 1, 0)));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.getOffTarget()).isEmpty();
  }

  @Test
  void shouldFilterOutRowsForDeletedProcessDefinitions() {
    // Two overview rows exist in the index but only one definition is still imported. The stale
    // row for the deleted definition must not appear in the response and must not trigger the
    // backstop, otherwise the compute service would immediately recreate the row via its
    // synthetic-definition fallback and orphans would never age out.
    // given
    final BusinessValueOverviewDto liveRow =
        fresh(row(TENANT_A, "still-here", cyc(6_000L, 8_000L, true), autoNull(), 1, 1));
    final BusinessValueOverviewDto orphanRow =
        stamp(
            row(TENANT_A, "deleted-proc", cyc(9_000L, 8_000L, false), autoNull(), 1, 0),
            NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS));
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any()))
        .thenReturn(List.of(liveRow, orphanRow));
    stubCurrentDefinitions(new DefKey(TENANT_A, "still-here"));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.getCoverage().getTotalProcesses()).isEqualTo(1);
    assertThat(response.getCoverage().getProcessesWithTarget()).isEqualTo(1);
    assertThat(response.getAttainment().getTargetsSet()).isEqualTo(1);
    assertThat(response.getAttainment().getTargetsMet()).isEqualTo(1);
    assertThat(response.getOffTarget()).isEmpty();

    Awaitility.await()
        .during(java.time.Duration.ofMillis(150))
        .atMost(java.time.Duration.ofMillis(500))
        .untilAsserted(() -> verify(computeService, never()).computeOverviewRows(anyList()));
  }

  @Test
  void shouldReturnEmptyResponseWhenEveryRowIsOrphaned() {
    // given
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any()))
        .thenReturn(
            List.of(fresh(row(TENANT_A, "gone-1", cyc(6_000L, 8_000L, true), autoNull(), 1, 1))));
    when(definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS))
        .thenReturn(List.of());

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.isHasAnyTarget()).isFalse();
    assertThat(response.getCoverage().getTotalProcesses()).isZero();
    assertThat(response.getOffTarget()).isEmpty();
  }

  @Test
  void shouldFireFullScopeBackstopWhenAnyRowIsStale() {
    // A single stale row triggers a full-scope recompute across every range. Firing per row
    // would flood the common pool when N rows are simultaneously stale (post-deploy, missed
    // scheduler tick); the response-level flag collapses that fan-out into one job.
    // given
    final BusinessValueOverviewDto staleRow =
        stamp(
            row(TENANT_A, "proc-stale", cyc(6_000L, 8_000L, true), autoNull(), 1, 1),
            NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS));
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any()))
        .thenReturn(List.of(staleRow));
    stubCurrentDefinitions(new DefKey(TENANT_A, "proc-stale"));

    // when
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    Awaitility.await()
        .untilAsserted(
            () -> verify(computeService).computeOverviewRows(eq(List.of(MetricRange.values()))));
  }

  @Test
  void shouldNotFireTheBackstopWhenComputeIsDisabled() {
    // given a stale row that would otherwise trigger a recompute, and the sweep switched off
    businessValueConfiguration.setOverviewComputeEnabled(false);
    final BusinessValueOverviewDto staleRow =
        stamp(
            row(TENANT_A, "proc-stale", cyc(6_000L, 8_000L, true), autoNull(), 1, 1),
            NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS));
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any()))
        .thenReturn(List.of(staleRow));
    stubCurrentDefinitions(new DefKey(TENANT_A, "proc-stale"));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then no recompute is queued — otherwise disabling the sweep would still let every read spawn
    // the work it was disabled to prevent — and the read still serves its (now frozen) rows
    verify(computeService, never()).computeOverviewRows(any());
    assertThat(response).isNotNull();
    assertThat(response.getCoverage().getTotalProcesses()).isEqualTo(1);
  }

  @Test
  void shouldNotFireBackstopWhenFresh() {
    // given
    seedRows(
        MetricRange.THIRTY_DAYS,
        fresh(row(TENANT_A, "proc-fresh", cyc(6_000L, 8_000L, true), autoNull(), 1, 1)));

    // when
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    Awaitility.await()
        .during(java.time.Duration.ofMillis(150))
        .atMost(java.time.Duration.ofMillis(500))
        .untilAsserted(() -> verify(computeService, never()).computeOverviewRows(anyList()));
  }

  @Test
  void shouldFireBackstopOnceEvenWhenManyRowsAreStale() {
    // Every row stale is the load pattern that motivated the flag — post-deploy or missed
    // scheduler tick, the whole page is stale at once. One recompute must cover it, not N.
    // given
    final List<BusinessValueOverviewDto> staleRows =
        List.of(
            stamp(
                row(TENANT_A, "proc-a", cyc(6_000L, 8_000L, true), autoNull(), 1, 1),
                NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS)),
            stamp(
                row(TENANT_A, "proc-b", cyc(6_000L, 8_000L, true), autoNull(), 1, 1),
                NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS)),
            stamp(
                row(TENANT_A, "proc-c", cyc(6_000L, 8_000L, true), autoNull(), 1, 1),
                NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS)));
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any())).thenReturn(staleRows);
    stubCurrentDefinitions(
        new DefKey(TENANT_A, "proc-a"),
        new DefKey(TENANT_A, "proc-b"),
        new DefKey(TENANT_A, "proc-c"));

    // when
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    Awaitility.await()
        .untilAsserted(() -> verify(computeService, times(1)).computeOverviewRows(anyList()));
  }

  @Test
  void shouldCollapseConcurrentBackstopsIntoASingleRecompute() {
    // Two overlapping reads while the first backstop is still running must not schedule a second
    // full-scope job — the in-flight flag blocks re-entry until the compute future settles.
    // given
    final BusinessValueOverviewDto staleRow =
        stamp(
            row(TENANT_A, "proc-hot", cyc(6_000L, 8_000L, true), autoNull(), 1, 1),
            NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS));
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any()))
        .thenReturn(List.of(staleRow));
    stubCurrentDefinitions(new DefKey(TENANT_A, "proc-hot"));

    // when
    final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              release.await();
              return null;
            })
        .when(computeService)
        .computeOverviewRows(anyList());

    // then
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    Awaitility.await()
        .during(java.time.Duration.ofMillis(200))
        .atMost(java.time.Duration.ofMillis(500))
        .untilAsserted(() -> verify(computeService, times(1)).computeOverviewRows(anyList()));

    release.countDown();
  }

  @Test
  void shouldHealEveryRangeInASingleBackstopCall() {
    // The backstop's purpose is to recover from a stopped scheduler; that scheduler sweeps every
    // range in one pass, so the backstop must do the same. If it only healed the current range,
    // switching the range tab after a deploy would trigger another wave of compute work.
    // given
    final BusinessValueOverviewDto staleRow =
        stamp(
            row(TENANT_A, "proc-x", cyc(6_000L, 8_000L, true), autoNull(), 1, 1),
            NOW.minusSeconds(3L * REFRESH_INTERVAL_SECONDS));
    when(overviewRepository.readByRange(eq(MetricRange.SIX_MONTHS), any()))
        .thenReturn(List.of(staleRow));
    stubCurrentDefinitions(new DefKey(TENANT_A, "proc-x"));

    // when
    readService.getOverview(USER, MetricRange.SIX_MONTHS);

    // then
    final ArgumentCaptor<List<MetricRange>> ranges = argCaptor();
    Awaitility.await()
        .untilAsserted(() -> verify(computeService).computeOverviewRows(ranges.capture()));
    assertThat(ranges.getValue()).containsExactlyInAnyOrder(MetricRange.values());
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

  /**
   * The sweep only measures targeted definitions, so an untargeted row is never recomputed and its
   * timestamp ages without bound. Counting those as stale would leave the backstop firing a
   * fleet-wide recompute on every single read, forever, from the moment one untargeted definition
   * exists.
   */
  @Test
  void shouldNotTriggerTheBackstopForAnUntargetedRowHoweverOldItIs() {
    // given a row with no target, last computed long before the stale threshold
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any()))
        .thenReturn(
            List.of(
                stamp(
                    row(TENANT_A, "untargeted", cycNull(), autoNull(), 0, 0), NOW.minusYears(1))));
    stubCurrentDefinitions(new DefKey(TENANT_A, "untargeted"));

    // when
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    verify(computeService, never()).computeOverviewRows(any());
  }

  /** A targeted row that has genuinely gone stale must still trigger it. */
  @Test
  void shouldStillTriggerTheBackstopForAStaleTargetedRow() {
    // given a targeted row past the stale threshold
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any()))
        .thenReturn(
            List.of(
                stamp(
                    row(TENANT_A, "targeted", cyc(5_000L, 1_000L, false), autoNull(), 1, 0),
                    NOW.minusYears(1))));
    stubCurrentDefinitions(new DefKey(TENANT_A, "targeted"));

    // when
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    Awaitility.await().untilAsserted(() -> verify(computeService).computeOverviewRows(any()));
  }

  // --- target-only definitions: imported since the last sweep, so no computed row yet ---

  /**
   * Setting a target on a definition the sweep has not measured yet used to leave it absent from L0
   * entirely — the target was saved, and nothing showed it. The entry is what is actually known: a
   * target, and no measurement to judge it against.
   */
  @Test
  void shouldSurfaceADefinitionThatHasATargetButNoComputedRow() {
    // given a current definition with a target and no row
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any())).thenReturn(List.of());
    stubCurrentDefinitions(new DefKey(TENANT_A, "fresh-import"));
    when(targetRepository.readByTenants(any()))
        .thenReturn(List.of(target(TENANT_A, "fresh-import", 1_000L, 90)));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then it counts towards coverage and targets set, but nothing is claimed to be met
    assertThat(response.isHasAnyTarget()).isTrue();
    assertThat(response.getCoverage().getProcessesWithTarget()).isEqualTo(1);
    assertThat(response.getCoverage().getTotalProcesses()).isEqualTo(1);
    assertThat(response.getAttainment().getTargetsSet()).isEqualTo(2);
    assertThat(response.getAttainment().getTargetsMet()).isZero();
  }

  /** There is no measurement, so there is no gap to rank — an entry here would be fabricated. */
  @Test
  void shouldNotListATargetOnlyDefinitionAsOffTarget() {
    // given
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any())).thenReturn(List.of());
    stubCurrentDefinitions(new DefKey(TENANT_A, "fresh-import"));
    when(targetRepository.readByTenants(any()))
        .thenReturn(List.of(target(TENANT_A, "fresh-import", 1_000L, 90)));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.getOffTarget()).isEmpty();
  }

  /**
   * These rows were never computed, so an honest timestamp would read as stale and fire a
   * fleet-wide recompute on every single read for as long as one target-only definition exists.
   */
  @Test
  void shouldNotTriggerTheBackstopForATargetOnlyDefinition() {
    // given only a target-only definition, and a sweep that is otherwise up to date
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any())).thenReturn(List.of());
    stubCurrentDefinitions(new DefKey(TENANT_A, "fresh-import"));
    when(targetRepository.readByTenants(any()))
        .thenReturn(List.of(target(TENANT_A, "fresh-import", 1_000L, 90)));

    // when
    readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    verify(computeService, never()).computeOverviewRows(any());
  }

  /** A definition that already has a row must not be counted twice. */
  @Test
  void shouldNotDuplicateADefinitionThatAlreadyHasAComputedRow() {
    // given a definition with both a computed row and a target
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any()))
        .thenReturn(
            List.of(fresh(row(TENANT_A, "invoice", cyc(5_000L, 1_000L, false), autoNull(), 1, 0))));
    stubCurrentDefinitions(new DefKey(TENANT_A, "invoice"));
    when(targetRepository.readByTenants(any()))
        .thenReturn(List.of(target(TENANT_A, "invoice", 1_000L, null)));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.getCoverage().getTotalProcesses()).isEqualTo(1);
  }

  /**
   * The orphan filter applies to target-only entries too. A target outliving its definition would
   * otherwise resurrect it on L0 with no way to remove it.
   */
  @Test
  void shouldIgnoreATargetWhoseDefinitionNoLongerExists() {
    // given a target for a definition that is no longer imported
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any())).thenReturn(List.of());
    stubCurrentDefinitions();
    when(targetRepository.readByTenants(any()))
        .thenReturn(List.of(target(TENANT_A, "deleted-process", 1_000L, 90)));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then
    assertThat(response.isHasAnyTarget()).isFalse();
    assertThat(response.getCoverage().getTotalProcesses()).isZero();
  }

  /**
   * Clearing a target leaves its document behind with every field null. Synthesizing an entry for
   * one would add an untargeted process to the coverage denominator while a process that was never
   * targeted, and is otherwise identical, stays absent — so the denominator would depend on which
   * processes happened to carry a target at some point in the past.
   */
  @Test
  void shouldNotSurfaceADefinitionWhoseTargetWasCleared() {
    // given a current definition whose target document exists but holds no target values
    when(overviewRepository.readByRange(eq(MetricRange.THIRTY_DAYS), any())).thenReturn(List.of());
    stubCurrentDefinitions(new DefKey(TENANT_A, "was-targeted"));
    when(targetRepository.readByTenants(any()))
        .thenReturn(List.of(target(TENANT_A, "was-targeted", null, null)));

    // when
    final BusinessValueOverviewResponseDto response =
        readService.getOverview(USER, MetricRange.THIRTY_DAYS);

    // then it is absent, exactly as a never-targeted definition with no row would be
    assertThat(response.isHasAnyTarget()).isFalse();
    assertThat(response.getCoverage().getTotalProcesses()).isZero();
  }

  private static BusinessValueTargetDto target(
      final String tenantId,
      final String processKey,
      final Long cycleTimeMillis,
      final Integer automationRatePct) {
    return new BusinessValueTargetDto(
        processKey,
        tenantId,
        cycleTimeMillis,
        cycleTimeMillis == null ? null : TargetValueUnit.MILLIS,
        automationRatePct,
        OffsetDateTime.now(ZoneOffset.UTC),
        "someone");
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
