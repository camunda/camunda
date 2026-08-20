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
import io.camunda.optimize.service.db.repository.MappingMetadataRepository;
import io.camunda.optimize.service.db.writer.BusinessValueOverviewWriter;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Materializes {@code business-value-overview} rows for every {@code (tenantId,
 * processDefinitionKey, metricRange)} triple across all fully-imported process definitions. Only
 * the two target-bearing seeded reports are evaluated ({@code bv-duration-by-process} and {@code
 * bv-automation-rate-by-process}) — volume has no target in v1 and is not part of the L0 rollup.
 *
 * <p>Both seeded reports group by process definition key, so one evaluation returns a bucket per
 * definition. Evaluation is therefore fanned in to once per {@code (report, metricRange, tenantId,
 * chunk)} rather than once per definition, which keeps the query count independent of how many
 * process definitions exist. Tenant is the finest scope a single evaluation can serve: the reports
 * group by definition key only, with no tenant dimension in the result, so definitions have to be
 * grouped by tenant for a per-tenant row to be correct.
 *
 * <p>Called from {@link BusinessValueOverviewSchedulerService} on each tick. The target-write and
 * stale-read backstop paths described in the technical design will call in from follow-up PRs once
 * the target REST endpoint and stale-read handler exist; this class currently exposes only the
 * scheduler entry point so unused surfaces don't linger.
 */
@Component
public class BusinessValueOverviewComputeService {

  /**
   * Upper bound on how many definitions are pinned onto a single report evaluation. Three separate
   * ceilings apply and the smallest wins: the group-by terms aggregation is sized at the configured
   * {@code aggregationBucketLimit} and silently drops buckets past it, every definition contributes
   * a {@code should} clause towards Elasticsearch's {@code indices.query.bool.max_clause_count}
   * (1024 by default on ES 7), and every definition adds one index alias to the request.
   */
  private static final int MAX_DEFINITIONS_PER_EVALUATION = 250;

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueOverviewComputeService.class);

  private final BusinessValueTargetRepository targetRepository;
  private final BusinessValueOverviewWriter overviewWriter;
  private final DefinitionService definitionService;
  private final ReportService reportService;
  private final PlainReportEvaluationHandler reportEvaluationHandler;
  private final MappingMetadataRepository mappingMetadataRepository;
  private final ConfigurationService configurationService;

  public BusinessValueOverviewComputeService(
      final BusinessValueTargetRepository targetRepository,
      final BusinessValueOverviewWriter overviewWriter,
      final DefinitionService definitionService,
      final ReportService reportService,
      final PlainReportEvaluationHandler reportEvaluationHandler,
      final MappingMetadataRepository mappingMetadataRepository,
      final ConfigurationService configurationService) {
    this.targetRepository = targetRepository;
    this.overviewWriter = overviewWriter;
    this.definitionService = definitionService;
    this.reportService = reportService;
    this.reportEvaluationHandler = reportEvaluationHandler;
    this.mappingMetadataRepository = mappingMetadataRepository;
    this.configurationService = configurationService;
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
    final Set<String> keysWithInstanceIndex =
        mappingMetadataRepository.getProcessDefinitionKeysWithInstanceIndex();
    final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    final Map<String, List<DefinitionEntry>> definitionsByTenant =
        definitions.stream()
            .collect(
                Collectors.groupingBy(
                    DefinitionEntry::tenantId, LinkedHashMap::new, Collectors.toList()));

    final List<BusinessValueOverviewDto> rowsToUpsert = new ArrayList<>();
    for (final Map.Entry<String, List<DefinitionEntry>> perTenant :
        definitionsByTenant.entrySet()) {
      final String tenantId = perTenant.getKey();
      final List<DefinitionEntry> tenantDefinitions = perTenant.getValue();
      final List<List<DefinitionEntry>> chunks =
          chunk(evaluableDefinitions(tenantDefinitions, keysWithInstanceIndex));

      for (final MetricRange range : ranges) {
        final Map<String, Double> cycleMillisByKey =
            evaluateChunks(
                BusinessValueDashboardService.DURATION_BY_PROCESS_REPORT_ID,
                range,
                tenantId,
                chunks);
        final Map<String, Double> automationPctByKey =
            evaluateChunks(
                BusinessValueDashboardService.AUTOMATION_RATE_BY_PROCESS_REPORT_ID,
                range,
                tenantId,
                chunks);

        for (final DefinitionEntry def : tenantDefinitions) {
          rowsToUpsert.add(
              buildRow(
                  def,
                  range,
                  cycleMillisByKey.get(def.processDefinitionKey()),
                  automationPctByKey.get(def.processDefinitionKey()),
                  targetsByDocId,
                  now));
        }
      }
    }

    overviewWriter.bulkUpsertFromScheduler(rowsToUpsert);
  }

  /** Evaluates one report across every chunk of a tenant's definitions and merges the results. */
  private Map<String, Double> evaluateChunks(
      final String reportId,
      final MetricRange range,
      final String tenantId,
      final List<List<DefinitionEntry>> chunks) {
    final Map<String, Double> valuesByKey = new HashMap<>();
    for (final List<DefinitionEntry> chunk : chunks) {
      valuesByKey.putAll(evaluateByDefinitionKey(reportId, range, tenantId, chunk));
    }
    return valuesByKey;
  }

  /**
   * Drops definitions with no process instance index. That index is created by {@code
   * ProcessInstanceWriter} on the first instance import, so a definition that is deployed but never
   * run has none, and naming a missing index makes the search fail with {@code index_not_found}.
   * With several definitions pinned to one evaluation the interpreter answers that failure by
   * retrying the whole query against the process instance multi alias — correct results, but every
   * instance index in the cluster gets opened instead of the ones asked for.
   *
   * <p>Nothing is lost by dropping them: no instance index means no instances, so no completed
   * instances, so the value is null either way. They still get a row, with null values.
   *
   * <p>Keys are matched case-insensitively because {@link
   * io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex#constructIndexName} lowercases
   * the definition key to build the index name, while the definition itself keeps the casing of the
   * BPMN process id. Comparing them directly would drop every definition whose key contains an
   * uppercase character — {@code Process_1} and friends — from the query.
   */
  private static List<DefinitionEntry> evaluableDefinitions(
      final List<DefinitionEntry> definitions, final Set<String> keysWithInstanceIndex) {
    final Set<String> normalized =
        keysWithInstanceIndex.stream()
            .map(key -> key.toLowerCase(Locale.ENGLISH))
            .collect(Collectors.toSet());
    return definitions.stream()
        .filter(def -> normalized.contains(def.processDefinitionKey().toLowerCase(Locale.ENGLISH)))
        .toList();
  }

  private List<List<DefinitionEntry>> chunk(final List<DefinitionEntry> definitions) {
    final int chunkSize = maxDefinitionsPerEvaluation();
    final List<List<DefinitionEntry>> chunks = new ArrayList<>();
    for (int start = 0; start < definitions.size(); start += chunkSize) {
      chunks.add(definitions.subList(start, Math.min(start + chunkSize, definitions.size())));
    }
    return chunks;
  }

  /**
   * The active engine's bucket limit is not reachable without knowing which engine is configured,
   * so both are consulted and the smaller wins. Erring small only ever costs an extra evaluation;
   * erring large silently drops definitions past the bucket limit.
   */
  private int maxDefinitionsPerEvaluation() {
    return Math.min(
        MAX_DEFINITIONS_PER_EVALUATION,
        Math.min(
            configurationService.getElasticSearchConfiguration().getAggregationBucketLimit(),
            configurationService.getOpenSearchConfiguration().getAggregationBucketLimit()));
  }

  private BusinessValueOverviewDto buildRow(
      final DefinitionEntry def,
      final MetricRange range,
      final Double cycleMillis,
      final Double automationPct,
      final Map<String, BusinessValueTargetDto> targetsByDocId,
      final OffsetDateTime now) {
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
   * Evaluates a seeded business-value report once for a whole chunk of definitions belonging to one
   * tenant, returning each definition's value keyed by process definition key. Both seeded reports
   * group by process definition key, so a single evaluation yields one bucket per definition — the
   * per-definition value comes out of the result map instead of costing its own evaluation.
   *
   * <p>Clearing the {@code businessValueReport} flag on the in-memory copy is what makes the pinned
   * definitions survive. {@link
   * io.camunda.optimize.service.db.report.ReportEvaluationHandler#setDataSourcesForSystemGeneratedReports}
   * overwrites the definitions of any report carrying that flag: with no definitions supplied
   * through the additional filters it substitutes every fully-imported definition across every
   * tenant, which would both blend tenants into one bucket and push the definition count past the
   * aggregation bucket limit this method chunks to stay under. Supplying them through the
   * additional filters instead is no better — that path resolves each key with its own search and
   * still replaces the tenant list with every tenant the definition exists on.
   *
   * <p>The report is fetched fresh per evaluation on purpose: evaluation appends the additional
   * filters onto the report's own filter list, so a reused instance would accumulate one rolling
   * date filter per range and silently narrow every subsequent range.
   */
  private Map<String, Double> evaluateByDefinitionKey(
      final String reportId,
      final MetricRange range,
      final String tenantId,
      final List<DefinitionEntry> definitions) {
    if (definitions.isEmpty()) {
      return Map.of();
    }

    final ReportDefinitionDto<?> report = reportService.getReportDefinition(reportId);
    if (!(report.getData() instanceof final ProcessReportDataDto reportData)) {
      throw new IllegalStateException(
          "Seeded business-value report [" + reportId + "] is not a process report");
    }
    reportData.setBusinessValueReport(false);
    reportData.setDefinitions(
        definitions.stream()
            .map(def -> new ReportDataDefinitionDto(def.processDefinitionKey(), List.of(tenantId)))
            .toList());

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
      return Map.of();
    }

    final Map<String, Double> valuesByKey = new HashMap<>(entries.size());
    for (final MapResultEntryDto entry : entries) {
      // A definition with no completed instances in the window produces no bucket at all, so it is
      // simply absent here and its row keeps null values.
      if (entry.getKey() != null && entry.getValue() != null) {
        valuesByKey.put(entry.getKey(), entry.getValue());
      }
    }
    return valuesByKey;
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
