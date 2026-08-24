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
import io.camunda.optimize.service.db.repository.SearchLimitsRepository;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.db.writer.BusinessValueOverviewWriter;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.nio.charset.StandardCharsets;
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
 * chunk)} rather than once per definition, so the query count scales with the number of chunks
 * rather than with the definition count itself — a catalog of 1000 definitions on one tenant costs
 * 32 evaluations per sweep instead of 8000. Tenant is the finest scope a single evaluation can
 * serve: the reports group by definition key only, with no tenant dimension in the result, so
 * definitions have to be grouped by tenant for a per-tenant row to be correct.
 *
 * <p>Called from {@link BusinessValueOverviewSchedulerService} on each tick, and from {@code
 * BusinessValueOverviewReadService}'s stale-read backstop on the common pool. Those two can
 * overlap: the backstop's in-flight guard only excludes a second backstop, not a concurrent
 * scheduler tick, so two full-scope sweeps may build chunk state and upsert the same document ids
 * at once. That is tolerable because a row is a pure function of its inputs and the writes are
 * idempotent, but it is worth knowing before adding per-sweep state that is not.
 */
@Component
public class BusinessValueOverviewComputeService {

  /**
   * Byte budget for the index names a single evaluation may target. Every pinned definition adds
   * its {@code process-instance-<key>} alias to the search request, and those aliases travel in the
   * request <em>line</em> (the URL path), not the body — so the binding ceiling is Elasticsearch's
   * {@code http.max_initial_line_length}, 4 KB by default and not configured in this repository.
   *
   * <p>Exceeding it fails the request with a {@code 400}, which — unlike {@code index_not_found} —
   * has no retry path in the interpreter, so the exception propagates and the whole sweep dies.
   * Chunking by <em>count</em> cannot prevent that, because the length of a chunk depends on how
   * long the BPMN process ids happen to be; hence a byte budget. 3 KB leaves room for the rest of
   * the request line.
   */
  private static final int INDEX_NAME_BUDGET_BYTES = 3_000;

  /** The comma joining one alias to the next in the request line. */
  private static final int ALIAS_SEPARATOR_BYTES = 1;

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BusinessValueOverviewComputeService.class);

  private final BusinessValueTargetRepository targetRepository;
  private final BusinessValueOverviewWriter overviewWriter;
  private final DefinitionService definitionService;
  private final ReportService reportService;
  private final PlainReportEvaluationHandler reportEvaluationHandler;
  private final MappingMetadataRepository mappingMetadataRepository;
  private final SearchLimitsRepository searchLimitsRepository;
  private final ConfigurationService configurationService;

  public BusinessValueOverviewComputeService(
      final BusinessValueTargetRepository targetRepository,
      final BusinessValueOverviewWriter overviewWriter,
      final DefinitionService definitionService,
      final ReportService reportService,
      final PlainReportEvaluationHandler reportEvaluationHandler,
      final MappingMetadataRepository mappingMetadataRepository,
      final SearchLimitsRepository searchLimitsRepository,
      final ConfigurationService configurationService) {
    this.targetRepository = targetRepository;
    this.overviewWriter = overviewWriter;
    this.definitionService = definitionService;
    this.reportService = reportService;
    this.reportEvaluationHandler = reportEvaluationHandler;
    this.mappingMetadataRepository = mappingMetadataRepository;
    this.searchLimitsRepository = searchLimitsRepository;
    this.configurationService = configurationService;
  }

  public void computeOverviewRows(final List<MetricRange> ranges) {
    if (ranges == null || ranges.isEmpty()) {
      throw new IllegalArgumentException("ranges must not be null or empty");
    }

    // Guarded here rather than at the call sites so every caller is covered — the scheduler tick,
    // the stale-read backstop, and whatever calls in next — and so flipping the flag takes effect
    // on the next invocation without a restart, which is the point of having it during an incident.
    if (!configurationService.getBusinessValueConfiguration().isOverviewComputeEnabled()) {
      LOG.info(
          "Business-value overview compute is disabled by configuration; skipping. "
              + "Existing rows remain readable but will not advance until it is re-enabled.");
      return;
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
      rowsToUpsert.addAll(
          rowsForTenant(
              perTenant.getKey(),
              perTenant.getValue(),
              ranges,
              keysWithInstanceIndex,
              targetsByDocId,
              now));
    }

    overviewWriter.bulkUpsertFromScheduler(rowsToUpsert);
  }

  /**
   * Evaluates one tenant's definitions across every range and assembles their rows. Chunking is per
   * tenant because the reports group by definition key alone, with no tenant dimension in the
   * result — so the saving is proportional to how many definitions this one tenant owns.
   */
  private List<BusinessValueOverviewDto> rowsForTenant(
      final String tenantId,
      final List<DefinitionEntry> tenantDefinitions,
      final List<MetricRange> ranges,
      final Set<String> keysWithInstanceIndex,
      final Map<String, BusinessValueTargetDto> targetsByDocId,
      final OffsetDateTime now) {
    final List<List<DefinitionEntry>> chunks =
        chunk(evaluableDefinitions(tenantDefinitions, keysWithInstanceIndex));
    final List<BusinessValueOverviewDto> rows = new ArrayList<>();

    for (final MetricRange range : ranges) {
      final Map<String, Double> cycleMillisByKey =
          evaluateChunks(
              BusinessValueDashboardService.DURATION_BY_PROCESS_REPORT_ID, range, tenantId, chunks);
      final Map<String, Double> automationPctByKey =
          evaluateChunks(
              BusinessValueDashboardService.AUTOMATION_RATE_BY_PROCESS_REPORT_ID,
              range,
              tenantId,
              chunks);

      for (final DefinitionEntry def : tenantDefinitions) {
        rows.add(
            buildRow(
                def,
                range,
                cycleMillisByKey.get(def.processDefinitionKey()),
                automationPctByKey.get(def.processDefinitionKey()),
                targetsByDocId,
                now));
      }
    }
    return rows;
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
   * run has none. Naming a missing index makes the search fail with {@code index_not_found}; with
   * several definitions pinned the interpreter recovers by retrying the whole query against the
   * process instance multi alias, so results stay correct but every instance index in the cluster
   * gets opened instead of the ones asked for. (A single-definition request has no such retry and
   * falls through to an empty result instead.)
   *
   * <p>Nothing is lost by dropping them: no instance index means no instances, so no completed
   * instances, so the value is null either way. They still get a row, with null values.
   *
   * <p>{@link MappingMetadataRepository#getProcessDefinitionKeysWithInstanceIndex()} promises
   * lowercased keys, because index names are built by lowercasing the definition key. The
   * definition keeps the casing of its BPMN process id, so the definition side is lowercased here
   * to match — without it every mixed-case key ({@code Process_1} and friends) would be dropped
   * from the query and left with a permanently null-valued row.
   */
  private static List<DefinitionEntry> evaluableDefinitions(
      final List<DefinitionEntry> definitions, final Set<String> lowercasedKeysWithInstanceIndex) {
    return definitions.stream()
        .filter(
            def ->
                lowercasedKeysWithInstanceIndex.contains(
                    def.processDefinitionKey().toLowerCase(Locale.ENGLISH)))
        .toList();
  }

  /**
   * Splits a tenant's definitions into chunks that are safe to pin onto one evaluation. Two
   * ceilings apply independently: the index names must fit the request line ({@link
   * #INDEX_NAME_BUDGET_BYTES}) and the group-by terms aggregation must be able to return a bucket
   * per definition (the active engine's aggregation bucket limit, which silently drops buckets past
   * it).
   *
   * <p>A single definition whose name alone exceeds the byte budget still gets its own chunk rather
   * than being dropped — one oversized request is a better failure than a silently missing row.
   */
  private List<List<DefinitionEntry>> chunk(final List<DefinitionEntry> definitions) {
    final int maxDefinitions = searchLimitsRepository.aggregationBucketLimit();
    final String indexPrefix = searchLimitsRepository.indexNamePrefix();
    final List<List<DefinitionEntry>> chunks = new ArrayList<>();
    List<DefinitionEntry> current = new ArrayList<>();
    int currentBytes = 0;

    for (final DefinitionEntry def : definitions) {
      final int cost = requestLineCostBytes(indexPrefix, def.processDefinitionKey());
      final boolean full =
          !current.isEmpty()
              && (currentBytes + cost > INDEX_NAME_BUDGET_BYTES
                  || current.size() >= maxDefinitions);
      if (full) {
        chunks.add(current);
        current = new ArrayList<>();
        currentBytes = 0;
      }
      current.add(def);
      currentBytes += cost;
    }
    if (!current.isEmpty()) {
      chunks.add(current);
    }
    return chunks;
  }

  /**
   * Bytes this definition's alias occupies in the request line, counted as it is actually encoded
   * rather than as characters.
   *
   * <p>{@code http.max_initial_line_length} limits bytes on the wire, and index names reach
   * Elasticsearch percent-encoded in the URL path. A BPMN process id is an XML NCName, so non-ASCII
   * letters are legal and the engines accept them in index names — and there they are anything but
   * one byte each. {@code ü} arrives as {@code %C3%BC}, six bytes; a CJK character costs nine.
   * Counting characters would let a chunk of such names pass this budget and still overrun the
   * limit, which fails the request with a 400 that has no retry path and takes the sweep with it.
   *
   * <p>ASCII names are unaffected — an unreserved character still costs one — so the common case
   * chunks exactly as before.
   */
  private static int requestLineCostBytes(final String indexPrefix, final String definitionKey) {
    final String alias = indexPrefix + "-" + ProcessInstanceIndex.constructIndexName(definitionKey);
    int bytes = ALIAS_SEPARATOR_BYTES;
    for (final byte encoded : alias.getBytes(StandardCharsets.UTF_8)) {
      bytes += isUnreservedInPath(encoded) ? 1 : 3;
    }
    return bytes;
  }

  /** Unreserved per RFC 3986; anything else is percent-encoded to three characters per byte. */
  private static boolean isUnreservedInPath(final byte encoded) {
    final int c = encoded & 0xFF;
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || c == '-'
        || c == '.'
        || c == '_'
        || c == '~';
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
   * overwrites the definitions of any report carrying that flag, and with no definitions supplied
   * through the additional filters it takes the branch that substitutes every fully-imported
   * definition. That branch resolves them for a user, and this sweep has no user — so with the flag
   * left set the evaluation fails outright with {@code ForbiddenException("userId is null")} rather
   * than returning blended data. Either way the pinned scope is gone.
   *
   * <p>Supplying the definitions through the additional filters instead is no better: that path
   * resolves each key with its own search — reinstating the per-definition fan-out this method
   * exists to remove — and replaces the tenant list with every tenant the definition exists on,
   * which would blend tenants into a single bucket since the report carries no tenant dimension.
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
