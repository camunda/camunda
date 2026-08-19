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
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.tenant.TenantService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
 */
@Component
public class BusinessValueOverviewReadService {

  private static final String CYCLE_TIME_DISPLAY_UNIT = "HOURS";
  private static final String AUTOMATION_RATE_DISPLAY_UNIT = "PERCENT";

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueOverviewReadService.class);

  private final BusinessValueOverviewRepository overviewRepository;
  private final TenantService tenantService;
  private final DefinitionService definitionService;
  private final BusinessValueOverviewComputeService computeService;
  private final ConfigurationService configurationService;

  /**
   * Coalesces backstop recomputes across concurrent readers so a single stale row triggers one
   * background compute, not one per in-flight request. Entries are keyed by (tenantId,
   * processDefinitionKey, metricRange) and removed once the compute future completes.
   */
  private final ConcurrentHashMap<BackstopKey, Boolean> inFlightBackstops =
      new ConcurrentHashMap<>();

  public BusinessValueOverviewReadService(
      final BusinessValueOverviewRepository overviewRepository,
      final TenantService tenantService,
      final DefinitionService definitionService,
      final BusinessValueOverviewComputeService computeService,
      final ConfigurationService configurationService) {
    this.overviewRepository = overviewRepository;
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

    if (rawRows.isEmpty()) {
      return emptyResponse();
    }

    // Overview rows for deleted process definitions would otherwise persist forever — the
    // scheduler stops upserting them but the repository has no deletion path today. Intersecting
    // against the current fully-imported definition set at read time hides orphans immediately,
    // and skipping them here also prevents the stale-read backstop from resurrecting them via
    // computeService (which would otherwise fall back to a synthetic definition entry).
    // Storage-level cleanup is tracked separately.
    final Set<DefinitionKey> currentDefinitions = currentDefinitions();
    final List<BusinessValueOverviewDto> rows =
        rawRows.stream()
            .filter(
                row ->
                    currentDefinitions.contains(
                        new DefinitionKey(row.getTenantId(), row.getProcessDefinitionKey())))
            .toList();

    if (rows.isEmpty()) {
      return emptyResponse();
    }

    final OffsetDateTime now = OffsetDateTime.now();
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

    for (final BusinessValueOverviewDto row : rows) {
      totalProcesses++;
      if (row.isHasAnyTarget()) {
        processesWithTarget++;
      }
      targetsSet += row.getTargetsSet();
      targetsMet += row.getTargetsMet();

      final CycleTimeBlock cycleTime = row.getCycleTime();
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

      if (isStale(row.getLastComputedAt(), now, staleThreshold)) {
        triggerBackstop(row.getTenantId(), row.getProcessDefinitionKey(), range);
      }
    }

    offTarget.sort(Comparator.comparingDouble(OffTargetEntryDto::gapPct).reversed());

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
        verdict.direction());
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

  private Set<DefinitionKey> currentDefinitions() {
    final List<DefinitionWithTenantIdsDto> definitions =
        definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS);
    final Set<DefinitionKey> keys = new HashSet<>();
    for (final DefinitionWithTenantIdsDto definition : definitions) {
      for (final String tenantId : definition.getTenantIds()) {
        keys.add(new DefinitionKey(tenantId, definition.getKey()));
      }
    }
    return keys;
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

  private void triggerBackstop(
      final String tenantId, final String processDefinitionKey, final MetricRange range) {
    final BackstopKey key = new BackstopKey(tenantId, processDefinitionKey, range);
    if (inFlightBackstops.putIfAbsent(key, Boolean.TRUE) != null) {
      // Another reader already scheduled the recompute for this row this refresh window.
      return;
    }
    LOG.info(
        "Stale-read backstop firing for tenantId={} processDefinitionKey={} range={}",
        tenantId,
        processDefinitionKey,
        range.getId());
    CompletableFuture.runAsync(
            () ->
                computeService.computeOverviewRows(
                    BusinessValueOverviewScope.definition(tenantId, processDefinitionKey),
                    List.of(range),
                    BusinessValueOverviewRefreshMode.SCHEDULER))
        .whenComplete(
            (result, throwable) -> {
              inFlightBackstops.remove(key);
              if (throwable != null) {
                LOG.warn(
                    "Stale-read backstop compute failed for tenantId={} processDefinitionKey={} range={}",
                    tenantId,
                    processDefinitionKey,
                    range.getId(),
                    throwable);
              }
            });
  }

  private record DefinitionKey(String tenantId, String processDefinitionKey) {
    private DefinitionKey {
      Objects.requireNonNull(tenantId, "tenantId");
      Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
    }
  }

  private record BackstopKey(String tenantId, String processDefinitionKey, MetricRange range) {
    private BackstopKey {
      Objects.requireNonNull(tenantId, "tenantId");
      Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
      Objects.requireNonNull(range, "range");
    }
  }
}
