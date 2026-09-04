/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.AutomationRateBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.CycleTimeBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto.AttainmentDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto.CategoryDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto.CoverageDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto.OffTargetEntryDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.db.repository.BusinessValueTargetRepository;
import io.camunda.optimize.service.tenant.TenantService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Serves {@code GET /business-value/overview?range=<preset>} by reading precomputed rows from the
 * {@code business-value-overview} index and assembling them per {@code
 * bvd-target-technical-design.md} §5.3. No live report evaluation on this path — every heavy
 * computation is already materialized by {@link BusinessValueOverviewComputeService}.
 *
 * <p>On each read, rows whose {@link BusinessValueOverviewDto#getLastComputedAt()} exceeds {@code 2
 * ×} the configured refresh interval are recomputed asynchronously as a safety net for missed
 * scheduler ticks (broker restart, deploy). The current request still returns the stale row; the
 * next reader sees fresh data.
 *
 * <p><b>Scale bound.</b> Per-request row count is capped at {@link
 * DatabaseConstants#LIST_FETCH_LIMIT} (1000) by the underlying {@code readByRange} query, scoped to
 * (range, authorized-tenant-set). Past that cap the repository throws {@code
 * OptimizeRuntimeException} and the endpoint 500s — never a silent truncation. A warning is logged
 * as the row count approaches the cap so ops sees it before a hard fail; the aggregation-based
 * rewrite that would remove the cap is tracked separately.
 */
@Component
public class BusinessValueOverviewReadService {

  private static final String CYCLE_TIME_DISPLAY_UNIT = "HOURS";
  private static final String AUTOMATION_RATE_DISPLAY_UNIT = "PERCENT";

  /**
   * The row count above which the endpoint logs a warning per request. Picked at 80% of the
   * repository fetch cap so operators see the approach several deployments before rows start
   * failing.
   */
  private static final int ROW_COUNT_WARNING_THRESHOLD =
      (int) (DatabaseConstants.LIST_FETCH_LIMIT * 0.8);

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueOverviewReadService.class);

  private final BusinessValueOverviewRepository overviewRepository;
  private final BusinessValueTargetRepository targetRepository;
  private final TenantService tenantService;
  private final DefinitionService definitionService;
  private final BusinessValueOverviewComputeService computeService;
  private final ConfigurationService configurationService;

  /**
   * Coalesces concurrent full-scope backstops so multiple in-flight readers can't queue a fresh
   * full-scope recompute on top of one that's already running. A stale request that arrives while a
   * backstop is in flight sees this flag set and skips scheduling another job.
   */
  private final AtomicBoolean backstopInFlight = new AtomicBoolean(false);

  public BusinessValueOverviewReadService(
      final BusinessValueOverviewRepository overviewRepository,
      final BusinessValueTargetRepository targetRepository,
      final TenantService tenantService,
      final DefinitionService definitionService,
      final BusinessValueOverviewComputeService computeService,
      final ConfigurationService configurationService) {
    this.overviewRepository = overviewRepository;
    this.targetRepository = targetRepository;
    this.tenantService = tenantService;
    this.definitionService = definitionService;
    this.computeService = computeService;
    this.configurationService = configurationService;
  }

  public BusinessValueOverviewResponseDto getOverview(
      final String userId, final MetricRange range) {
    final List<String> authorizedTenantIds = tenantService.getTenantIdsForUser(userId);
    final List<BusinessValueOverviewDto> rawRows =
        overviewRepository.readByRange(range, authorizedTenantIds);

    if (rawRows.size() >= ROW_COUNT_WARNING_THRESHOLD) {
      LOG.warn(
          "business-value overview read returned {} rows for range {} (fetch cap is {}); "
              + "approaching or hitting the LIST_FETCH_LIMIT ceiling.",
          rawRows.size(),
          range.getId(),
          DatabaseConstants.LIST_FETCH_LIMIT);
    }

    // Overview rows for deleted process definitions would otherwise persist forever — the
    // scheduler stops upserting them but the repository has no deletion path today. Intersecting
    // against the current fully-imported definition set at read time hides orphans immediately,
    // and skipping them here also prevents the stale-read backstop from resurrecting them via
    // computeService (which would otherwise fall back to a synthetic definition entry).
    // Storage-level cleanup is tracked separately.
    final Map<DefinitionKey, String> currentDefinitions = currentDefinitions();
    final List<BusinessValueOverviewDto> computedRows =
        rawRows.stream()
            .filter(
                row ->
                    currentDefinitions.containsKey(
                        new DefinitionKey(row.getTenantId(), row.getProcessDefinitionKey())))
            .toList();

    final OffsetDateTime now = OffsetDateTime.now();
    final List<BusinessValueOverviewDto> rows =
        withTargetOnlyDefinitions(
            computedRows, currentDefinitions, authorizedTenantIds, range, now);

    if (rows.isEmpty()) {
      return emptyResponse();
    }

    final Duration staleThreshold = staleThreshold();

    int totalProcesses = 0;
    int processesWithTarget = 0;
    int targetsSet = 0;
    int targetsMet = 0;
    int ctWithTarget = 0;
    int ctApplicable = 0;
    int ctMet = 0;
    int arWithTarget = 0;
    int arApplicable = 0;
    int arMet = 0;
    final List<OffTargetEntryDto> offTarget = new ArrayList<>();
    boolean anyRowStale = false;

    for (final BusinessValueOverviewDto row : rows) {
      totalProcesses++;
      if (row.isHasAnyTarget()) {
        processesWithTarget++;
      }
      targetsSet += row.getTargetsSet();
      targetsMet += row.getTargetsMet();

      final CycleTimeBlock cycleTime = row.getCycleTime();
      // v1: both KPIs are universally applicable to every process, so ctApplicable ends up equal
      // to totalProcesses on every response. The counter is kept as a distinct field so the
      // category donut denominator can diverge once a KPI becomes conditionally applicable (e.g.
      // automation rate on a process with zero user tasks); until then the FE can treat
      // totalApplicable and totalProcesses as interchangeable.
      ctApplicable++;
      if (cycleTime != null && cycleTime.getTarget() != null) {
        ctWithTarget++;
        if (Boolean.TRUE.equals(cycleTime.getMet())) {
          ctMet++;
        } else if (Boolean.FALSE.equals(cycleTime.getMet())
            && cycleTime.getValue() != null
            && cycleTime.getTarget() > 0L) {
          // A cycle-time target of zero would produce Infinity gapPct (division by zero) and break
          // the JSON contract. Front-end target validation is the source of truth; this guard
          // survives if a zero target slips past it.
          offTarget.add(
              buildOffTargetEntry(
                  row,
                  Kpi.CYCLE_TIME,
                  cycleTime.getValue().doubleValue(),
                  cycleTime.getTarget().doubleValue(),
                  CYCLE_TIME_DISPLAY_UNIT,
                  Direction.LOWER_IS_BETTER));
        }
      }

      final AutomationRateBlock automationRate = row.getAutomationRate();
      // Same v1 invariant as ctApplicable above — see comment.
      arApplicable++;
      if (automationRate != null && automationRate.getTarget() != null) {
        arWithTarget++;
        if (Boolean.TRUE.equals(automationRate.getMet())) {
          arMet++;
        } else if (Boolean.FALSE.equals(automationRate.getMet())
            && automationRate.getValue() != null
            && automationRate.getTarget() > 0) {
          // Same zero-target guard as cycle time: a 0% automation target divides by zero on gapPct.
          offTarget.add(
              buildOffTargetEntry(
                  row,
                  Kpi.AUTOMATION_RATE,
                  automationRate.getValue(),
                  automationRate.getTarget().doubleValue(),
                  AUTOMATION_RATE_DISPLAY_UNIT,
                  Direction.HIGHER_IS_BETTER));
        }
      }

      // Only targeted rows are measured by the sweep, so only they can be meaningfully stale. An
      // untargeted row is never recomputed and its timestamp ages without bound — counting those
      // would leave the backstop firing a fleet-wide recompute on every single read, forever, as
      // soon as one untargeted definition exists.
      if (row.isHasAnyTarget() && isStale(row.getLastComputedAt(), now, staleThreshold)) {
        anyRowStale = true;
      }
    }

    // Fire at most one background compute per read, and only if this reader is the first to
    // observe staleness. Firing one job per stale row would flood the common pool when every row
    // is stale (post-deploy, missed scheduler tick), so the response-level flag collapses that
    // fan-out into a single full-range recompute covering every (tenant, process, range) at once.
    if (anyRowStale) {
      triggerFullScopeBackstop();
    }

    offTarget.sort(Comparator.comparingDouble(OffTargetEntryDto::getGapPct).reversed());

    return new BusinessValueOverviewResponseDto(
        processesWithTarget > 0,
        new CoverageDto(processesWithTarget, totalProcesses),
        new AttainmentDto(targetsSet, targetsMet),
        List.of(
            new CategoryDto(Kpi.CYCLE_TIME.getId(), ctWithTarget, ctApplicable, ctMet),
            new CategoryDto(Kpi.AUTOMATION_RATE.getId(), arWithTarget, arApplicable, arMet)),
        offTarget);
  }

  private OffTargetEntryDto buildOffTargetEntry(
      final BusinessValueOverviewDto row,
      final Kpi kpi,
      final double value,
      final double target,
      final String displayUnit,
      final Direction direction) {
    final Verdict verdict = BusinessValueVerdict.verdict(kpi, value, target, direction);
    return new OffTargetEntryDto(
        row.getTenantId(),
        row.getProcessDefinitionKey(),
        row.getProcessDefinitionName(),
        kpi.getId(),
        verdict.value(),
        verdict.target(),
        displayUnit,
        verdict.gapPct(),
        verdict.direction()); // verdict.direction() is "over"/"under" — matches
    // OffTargetEntryDto.comparison
  }

  private BusinessValueOverviewResponseDto emptyResponse() {
    return new BusinessValueOverviewResponseDto(
        false,
        new CoverageDto(0, 0),
        new AttainmentDto(0, 0),
        List.of(
            new CategoryDto(Kpi.CYCLE_TIME.getId(), 0, 0, 0),
            new CategoryDto(Kpi.AUTOMATION_RATE.getId(), 0, 0, 0)),
        List.of());
  }

  private Map<DefinitionKey, String> currentDefinitions() {
    final List<DefinitionWithTenantIdsDto> definitions =
        definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS);
    final Map<DefinitionKey, String> keys = new HashMap<>();
    for (final DefinitionWithTenantIdsDto definition : definitions) {
      final String name = definition.getName() != null ? definition.getName() : definition.getKey();
      for (final String tenantId : definition.getTenantIds()) {
        keys.put(new DefinitionKey(tenantId, definition.getKey()), name);
      }
    }
    return keys;
  }

  /**
   * Adds an entry for every definition that has a target but no computed row yet.
   *
   * <p>A safety net rather than the usual path: {@link
   * BusinessValueOverviewComputeService#computeRowsForTarget} measures the definition and writes
   * its rows when the target is saved, so this only catches the cases where that did not happen —
   * the write-time measurement failed, or the sweep is switched off and the definition had no rows
   * to fall back on. Cheap enough to be worth keeping for those.
   *
   * <p>The entry carries the target and no values, which is what it is: the target is known, the
   * measurement is not. It therefore contributes to coverage and to the targets-set count, but is
   * excluded from the off-target list by the existing null-value guards — there is no measurement
   * to be off by.
   *
   * <p>Stamped with the current time so it never reads as stale. These rows were never computed, so
   * a truthful timestamp would trip the backstop into a fleet-wide recompute on every read for as
   * long as one target-only definition exists.
   *
   * <p>Not persisted. The sweep owns the index; this only shapes the response.
   */
  private List<BusinessValueOverviewDto> withTargetOnlyDefinitions(
      final List<BusinessValueOverviewDto> computedRows,
      final Map<DefinitionKey, String> currentDefinitions,
      final List<String> authorizedTenantIds,
      final MetricRange range,
      final OffsetDateTime now) {
    final Set<DefinitionKey> alreadyComputed = new HashSet<>();
    for (final BusinessValueOverviewDto row : computedRows) {
      alreadyComputed.add(new DefinitionKey(row.getTenantId(), row.getProcessDefinitionKey()));
    }

    final List<BusinessValueOverviewDto> synthesized = new ArrayList<>();
    for (final BusinessValueTargetDto target :
        targetRepository.readByTenants(authorizedTenantIds)) {
      final DefinitionKey key =
          new DefinitionKey(target.getTenantId(), target.getProcessDefinitionKey());
      // A cleared target keeps its document with every field null. Synthesizing for those would
      // add an untargeted process to the coverage denominator while an identical process that was
      // never targeted stays absent, making the denominator depend on target history.
      if (alreadyComputed.contains(key)
          || !currentDefinitions.containsKey(key)
          || !BusinessValueOverviewComputeService.hasAnyTarget(target)) {
        continue;
      }
      final BusinessValueOverviewDto row =
          new BusinessValueOverviewDto(
              target.getTenantId(),
              target.getProcessDefinitionKey(),
              currentDefinitions.get(key),
              range,
              now,
              BusinessValueVerdict.cycleTimeBlock(null, null),
              BusinessValueVerdict.automationRateBlock(null, null),
              false,
              0,
              0);
      BusinessValueOverviewComputeService.applyTarget(row, target);
      synthesized.add(row);
    }

    if (synthesized.isEmpty()) {
      return computedRows;
    }
    final List<BusinessValueOverviewDto> all = new ArrayList<>(computedRows);
    all.addAll(synthesized);
    return all;
  }

  private Duration staleThreshold() {
    final Long refreshInterval =
        configurationService.getBusinessValueConfiguration().getOverviewRefreshInterval();
    return Duration.ofSeconds(2L * refreshInterval);
  }

  private static boolean isStale(
      final OffsetDateTime lastComputedAt,
      final OffsetDateTime now,
      final Duration staleThreshold) {
    if (lastComputedAt == null) {
      return true;
    }
    return Duration.between(lastComputedAt, now).compareTo(staleThreshold) > 0;
  }

  private void triggerFullScopeBackstop() {
    // Checked before the coalescing flag so a disabled sweep does not leave it set, and so reads
    // do not queue work that would return immediately.
    if (!configurationService.getBusinessValueConfiguration().isOverviewComputeEnabled()) {
      return;
    }
    if (!backstopInFlight.compareAndSet(false, true)) {
      // Another reader has already scheduled the full-scope recompute; nothing to do.
      return;
    }
    LOG.info("Stale-read backstop firing full-scope recompute across every range and tenant pair.");
    CompletableFuture.runAsync(
            () -> computeService.computeOverviewRows(List.of(MetricRange.values())))
        .whenComplete(
            (result, throwable) -> {
              backstopInFlight.set(false);
              if (throwable != null) {
                LOG.warn("Stale-read backstop compute failed", throwable);
              }
            });
  }

  private record DefinitionKey(String tenantId, String processDefinitionKey) {}
}
