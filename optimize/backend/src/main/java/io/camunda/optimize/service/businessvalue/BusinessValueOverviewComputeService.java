/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.AutomationRateBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.CycleTimeBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.dashboard.BusinessValueDashboardService;
import io.camunda.optimize.service.db.report.PlainReportEvaluationHandler;
import io.camunda.optimize.service.db.report.ReportEvaluationInfo;
import io.camunda.optimize.service.db.report.result.MapCommandResult;
import io.camunda.optimize.service.db.repository.BusinessValueTargetRepository;
import io.camunda.optimize.service.db.writer.BusinessValueOverviewWriter;
import io.camunda.optimize.service.report.ReportService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Materializes {@code business-value-overview} rows for every {@code (tenantId,
 * processDefinitionKey, metricRange)} triple across all fully-imported process definitions. Only
 * the two target-bearing seeded reports are evaluated ({@code bv-duration-by-process} and {@code
 * bv-automation-rate-by-process}) — volume has no target in v1 and is not part of the L0 rollup.
 *
 * <p>Called from {@link BusinessValueOverviewSchedulerService} on each tick. The target-write and
 * stale-read backstop paths described in the technical design will call in from follow-up PRs once
 * the target REST endpoint and stale-read handler exist; this class currently exposes only the
 * scheduler entry point so unused surfaces don't linger.
 */
@Component
public class BusinessValueOverviewComputeService {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueOverviewComputeService.class);

  private final BusinessValueTargetRepository targetRepository;
  private final BusinessValueOverviewWriter overviewWriter;
  private final DefinitionService definitionService;
  private final ReportService reportService;
  private final PlainReportEvaluationHandler reportEvaluationHandler;

  public BusinessValueOverviewComputeService(
      final BusinessValueTargetRepository targetRepository,
      final BusinessValueOverviewWriter overviewWriter,
      final DefinitionService definitionService,
      final ReportService reportService,
      final PlainReportEvaluationHandler reportEvaluationHandler) {
    this.targetRepository = targetRepository;
    this.overviewWriter = overviewWriter;
    this.definitionService = definitionService;
    this.reportService = reportService;
    this.reportEvaluationHandler = reportEvaluationHandler;
  }

  public void computeOverviewRows(final List<MetricRange> ranges) {
    if (ranges == null || ranges.isEmpty()) {
      throw new IllegalArgumentException("ranges must not be null or empty");
    }

    final List<DefinitionEntry> definitions = resolveDefinitions();
    if (definitions.isEmpty()) {
      LOG.debug("No process definitions in scope for business-value overview compute; skipping");
      return;
    }

    final Map<String, BusinessValueTargetDto> targetsByDocId = readTargets();
    final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    final List<BusinessValueOverviewDto> rowsToUpsert = new ArrayList<>();
    for (final MetricRange range : ranges) {
      for (final DefinitionEntry def : definitions) {
        rowsToUpsert.add(buildRow(def, range, targetsByDocId, now));
      }
    }

    overviewWriter.bulkUpsertFromScheduler(rowsToUpsert);
  }

  private BusinessValueOverviewDto buildRow(
      final DefinitionEntry def,
      final MetricRange range,
      final Map<String, BusinessValueTargetDto> targetsByDocId,
      final OffsetDateTime now) {
    final Double cycleMillis =
        evaluatePerProcess(
            BusinessValueDashboardService.DURATION_BY_PROCESS_REPORT_ID,
            range,
            def.tenantId(),
            def.processDefinitionKey());
    final Double automationPct =
        evaluatePerProcess(
            BusinessValueDashboardService.AUTOMATION_RATE_BY_PROCESS_REPORT_ID,
            range,
            def.tenantId(),
            def.processDefinitionKey());

    final BusinessValueTargetDto target =
        targetsByDocId.get(targetDocId(def.tenantId(), def.processDefinitionKey()));

    final CycleTimeBlock cycleTime =
        BusinessValueVerdict.cycleTimeBlock(
            toLongMillis(cycleMillis), target == null ? null : target.getCycleTimeTargetMillis());
    final AutomationRateBlock automationRate =
        BusinessValueVerdict.automationRateBlock(
            automationPct, target == null ? null : target.getAutomationRateTargetPct());

    final int targetsSet =
        (cycleTime.getTarget() != null ? 1 : 0) + (automationRate.getTarget() != null ? 1 : 0);
    final int targetsMet =
        (Boolean.TRUE.equals(cycleTime.getMet()) ? 1 : 0)
            + (Boolean.TRUE.equals(automationRate.getMet()) ? 1 : 0);

    return new BusinessValueOverviewDto(
        def.tenantId(),
        def.processDefinitionKey(),
        def.processDefinitionName(),
        range,
        now,
        cycleTime,
        automationRate,
        targetsSet > 0,
        targetsSet,
        targetsMet);
  }

  /**
   * Evaluates a per-process seeded business-value report scoped to a single {@code (tenantId,
   * processDefinitionKey)} pair. {@link
   * io.camunda.optimize.service.db.report.ReportEvaluationHandler#setDataSourcesForSystemGeneratedReports}
   * would otherwise expand any business-value report to every tenant the definition exists on,
   * silently dropping the per-tenant scope and mixing metrics across tenants. Fetching the report
   * fresh, clearing the {@code businessValueReport} flag on the in-memory copy, and pinning the
   * definition to the exact {@code (key, tenantId)} pair here bypasses that path and preserves the
   * caller's tenant scoping.
   */
  private Double evaluatePerProcess(
      final String reportId,
      final MetricRange range,
      final String tenantId,
      final String processDefinitionKey) {
    final ReportDefinitionDto<?> report = reportService.getReportDefinition(reportId);
    if (!(report.getData() instanceof final ProcessReportDataDto reportData)) {
      throw new IllegalStateException(
          "Seeded business-value report [" + reportId + "] is not a process report");
    }
    reportData.setBusinessValueReport(false);
    reportData.setDefinitions(
        List.of(new ReportDataDefinitionDto(processDefinitionKey, List.of(tenantId))));

    final AdditionalProcessReportEvaluationFilterDto additionalFilters =
        new AdditionalProcessReportEvaluationFilterDto();
    additionalFilters.setFilter(completedInstancesInRolling(range));

    final SingleReportEvaluationResult<?> evaluationResult =
        (SingleReportEvaluationResult<?>)
            reportEvaluationHandler
                .evaluateReport(
                    ReportEvaluationInfo.builder(report)
                        .additionalFilters(additionalFilters)
                        .timezone(ZoneId.of("UTC"))
                        .build())
                .getEvaluationResult();

    final MapCommandResult commandResult =
        (MapCommandResult) evaluationResult.getFirstCommandResult();
    final List<MapResultEntryDto> entries = commandResult.getFirstMeasureData();
    if (entries == null) {
      return null;
    }
    for (final MapResultEntryDto entry : entries) {
      if (processDefinitionKey.equals(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }

  private List<DefinitionEntry> resolveDefinitions() {
    final List<DefinitionWithTenantIdsDto> allDefs =
        definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS);
    final List<DefinitionEntry> pairs = new ArrayList<>();
    for (final DefinitionWithTenantIdsDto def : allDefs) {
      final String name = def.getName() != null ? def.getName() : def.getKey();
      for (final String tenantId : def.getTenantIds()) {
        // The "not defined" tenant bucket surfaces as a literal null from DefinitionReader. Writer
        // validation rejects null tenantId — skip these rows so one un-tenanted definition doesn't
        // abort the whole sweep. C8 imports normalize empty tenant to <default>, so this only
        // filters legacy or malformed rows.
        if (tenantId == null) {
          LOG.debug(
              "Skipping business-value overview row for definition [{}] with null tenantId",
              def.getKey());
          continue;
        }
        pairs.add(new DefinitionEntry(tenantId, def.getKey(), name));
      }
    }
    return pairs;
  }

  private Map<String, BusinessValueTargetDto> readTargets() {
    final List<BusinessValueTargetDto> all = targetRepository.scanAll();
    if (all.size() >= LIST_FETCH_LIMIT) {
      // scanAll() is capped at LIST_FETCH_LIMIT. Any target rows beyond the cap are treated as
      // absent by the sweep and would overwrite existing overview rows with null targets. Log so
      // this is visible before it becomes a silent regression; paginated scan is a follow-up.
      LOG.warn(
          "Business-value target scan returned {} rows — at or past the LIST_FETCH_LIMIT cap. "
              + "Targets beyond the cap are missing from this sweep and their overview rows will "
              + "show no target until paginated scan is implemented.",
          all.size());
    }
    final Map<String, BusinessValueTargetDto> byId = new HashMap<>(all.size());
    for (final BusinessValueTargetDto row : all) {
      byId.put(targetDocId(row.getTenantId(), row.getProcessDefinitionKey()), row);
    }
    return byId;
  }

  private static String targetDocId(final String tenantId, final String processDefinitionKey) {
    return tenantId + BusinessValueTargetRepository.ID_SEPARATOR + processDefinitionKey;
  }

  private static Long toLongMillis(final Double value) {
    return value == null ? null : Math.round(value);
  }

  private static List<ProcessFilterDto<?>> completedInstancesInRolling(final MetricRange range) {
    final int value;
    final DateUnit unit;
    switch (range) {
      case SEVEN_DAYS -> {
        value = 7;
        unit = DateUnit.DAYS;
      }
      case THIRTY_DAYS -> {
        value = 30;
        unit = DateUnit.DAYS;
      }
      case THREE_MONTHS -> {
        value = 3;
        unit = DateUnit.MONTHS;
      }
      case SIX_MONTHS -> {
        value = 6;
        unit = DateUnit.MONTHS;
      }
      default -> throw new IllegalArgumentException("Unsupported metric range: " + range);
    }
    return ProcessFilterBuilder.filter()
        .completedInstancesOnly()
        .add()
        .rollingInstanceEndDate()
        .start((long) value, unit)
        .add()
        .buildList();
  }

  private record DefinitionEntry(
      String tenantId, String processDefinitionKey, String processDefinitionName) {}
}
