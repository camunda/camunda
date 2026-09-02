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
import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
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
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.db.repository.BusinessValueTargetRepository;
import io.camunda.optimize.service.db.repository.MappingMetadataRepository;
import io.camunda.optimize.service.db.repository.SearchLimitsRepository;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.db.writer.BusinessValueOverviewWriter;
import io.camunda.optimize.service.report.ReportService;
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
 *
 * <p>That purity holds only because targets are read immediately before the write rather than at
 * the start of the sweep — a snapshot taken minutes earlier is a second input with a lifetime of
 * its own, and two overlapping sweeps holding different ones produce different rows for the same
 * id. Keep target reads adjacent to the write if this is restructured.
 *
 * <h2>Known limitations</h2>
 *
 * <p>Rows are eventually consistent, converging within one sweep interval. Nothing accumulates and
 * no state is unrecoverable; the cases below trade a bounded window of slightly-stale measurements
 * for not serializing writes, which this module has no precedent for.
 *
 * <ul>
 *   <li><b>A target cleared while a sweep runs.</b> The definition was selected for measurement, so
 *       the sweep writes its row with the target and verdict cleared — correct, but carrying the
 *       measurements this sweep took rather than the fresher ones the save itself wrote. Only the
 *       newly-targeted direction is skipped before the write, because there the sweep has no
 *       measurements at all and would replace good values with nulls.
 *   <li><b>Two saves for the same definition at once.</b> Each applies the target it persisted, so
 *       neither can blend the other's values, but the writes are not ordered. If the later save's
 *       measurement finishes first, the earlier one lands last and the rows end up describing a
 *       target the target index no longer holds. Ordering this needs a version precondition or a
 *       per-definition lock; neither is worth it for a window this small, on an action a single
 *       user performs from one modal.
 * </ul>
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
  private final BusinessValueOverviewRepository overviewRepository;
  private final BusinessValueOverviewWriter overviewWriter;
  private final DefinitionService definitionService;
  private final ReportService reportService;
  private final PlainReportEvaluationHandler reportEvaluationHandler;
  private final MappingMetadataRepository mappingMetadataRepository;
  private final SearchLimitsRepository searchLimitsRepository;

  public BusinessValueOverviewComputeService(
      final BusinessValueTargetRepository targetRepository,
      final BusinessValueOverviewRepository overviewRepository,
      final BusinessValueOverviewWriter overviewWriter,
      final DefinitionService definitionService,
      final ReportService reportService,
      final PlainReportEvaluationHandler reportEvaluationHandler,
      final MappingMetadataRepository mappingMetadataRepository,
      final SearchLimitsRepository searchLimitsRepository) {
    this.targetRepository = targetRepository;
    this.overviewRepository = overviewRepository;
    this.overviewWriter = overviewWriter;
    this.definitionService = definitionService;
    this.reportService = reportService;
    this.reportEvaluationHandler = reportEvaluationHandler;
    this.mappingMetadataRepository = mappingMetadataRepository;
    this.searchLimitsRepository = searchLimitsRepository;
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

    // Read once here to decide what is worth measuring, and again below to decide what is written.
    // The two answer different questions and cannot be collapsed into one: this read has to happen
    // before the evaluations, the one below has to happen after them, and moving either to the
    // other side reintroduces a bug — reading only up front lets the sweep overwrite a target saved
    // mid-run, reading only at the end leaves nothing to select on. A target that lands between the
    // two is handled by dropping its row before the write; see below.
    final Set<String> targetedDocIds = targetedDocIds();
    if (targetedDocIds.isEmpty()) {
      LOG.debug("No business-value targets set; sweeping rows without measuring any definition");
    }

    final Set<String> keysWithInstanceIndex =
        mappingMetadataRepository.getProcessDefinitionKeysWithInstanceIndex();

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
              targetedDocIds));
    }

    // Targets are read here, after every evaluation has returned, rather than up front: the row
    // carries the target it was written with, and a target saved while the sweep was still
    // evaluating would otherwise be overwritten by the snapshot taken before it existed. The upsert
    // is a blind write with no version precondition, so the sweep always wins that exchange and the
    // user's target silently reverts until the next tick. Reading last narrows the window from the
    // whole sweep — tens of seconds, and longer the bigger the catalog — to the bulk write itself.
    final Map<String, BusinessValueTargetDto> targetsByDocId = readTargets();

    // A definition targeted after the selection snapshot was never evaluated, so the row built for
    // it here carries null values. The save that created that target has already measured the
    // definition and written real values, and upserting this row would replace them with nulls —
    // then stamp it fresh, so the backstop would not treat it as stale either. The user would see
    // "target set, no data" until the next sweep, which is the outcome this whole change exists to
    // prevent. Leave those rows out; the next sweep selects and measures them properly.
    rowsToUpsert.removeIf(
        row -> {
          final String docId = targetDocId(row.getTenantId(), row.getProcessDefinitionKey());
          final BusinessValueTargetDto target = targetsByDocId.get(docId);
          return target != null && hasAnyTarget(target) && !targetedDocIds.contains(docId);
        });

    // Stamped here rather than when the sweep started, so that whichever writer touches a row last
    // also leaves the latest timestamp on it. A sweep that began before a target write and finished
    // after it would otherwise move the row's freshness backwards, and the stale-read backstop keys
    // off exactly that field. The cost is that a long sweep overstates freshness by its own
    // duration, which is the price of the ordering property.
    final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    for (final BusinessValueOverviewDto row : rowsToUpsert) {
      row.setLastComputedAt(now);
      applyTarget(
          row, targetsByDocId.get(targetDocId(row.getTenantId(), row.getProcessDefinitionKey())));
    }

    overviewWriter.bulkUpsertFromScheduler(rowsToUpsert);
  }

  /**
   * Writes a target and the verdict it implies onto a row that already carries its measured values.
   * A {@code null} target clears both, which is what a cleared target must produce.
   *
   * <p>Separated from row construction so the target can be applied after every evaluation has
   * returned rather than from a snapshot taken before the first — see {@link #computeOverviewRows}.
   * {@link BusinessValueVerdict} is a pure function of the measured value and the target, so
   * applying it last produces the same row as applying it during construction would have.
   *
   * <p>All five fields move together because {@code BusinessValueOverviewWriter} rejects a row
   * whose {@code targetsSet} or {@code hasAnyTarget} disagrees with its blocks.
   */
  static void applyTarget(final BusinessValueOverviewDto row, final BusinessValueTargetDto target) {
    final CycleTimeBlock cycleTime =
        BusinessValueVerdict.cycleTimeBlock(
            row.getCycleTime() == null ? null : row.getCycleTime().getValue(),
            target == null ? null : target.getCycleTimeTargetMillis());
    final AutomationRateBlock automationRate =
        BusinessValueVerdict.automationRateBlock(
            row.getAutomationRate() == null ? null : row.getAutomationRate().getValue(),
            target == null ? null : target.getAutomationRateTargetPct());

    final int targetsSet =
        (cycleTime.getTarget() != null ? 1 : 0) + (automationRate.getTarget() != null ? 1 : 0);
    final int targetsMet =
        (Boolean.TRUE.equals(cycleTime.getMet()) ? 1 : 0)
            + (Boolean.TRUE.equals(automationRate.getMet()) ? 1 : 0);

    row.setCycleTime(cycleTime);
    row.setAutomationRate(automationRate);
    row.setHasAnyTarget(targetsSet > 0);
    row.setTargetsSet(targetsSet);
    row.setTargetsMet(targetsMet);
  }

  /**
   * Measures one definition and writes its overview rows, so the caller's next {@code GET
   * /business-value/overview} reflects a target the moment it is saved instead of waiting out a
   * sweep interval.
   *
   * <p>Delegates to the same {@link #rowsForTenant} the sweep uses, with a single-entry list. That
   * also covers a definition imported since the last sweep, which has no rows at all: rowsForTenant
   * builds a row per range for every definition it is given, evaluated or not, so the row is
   * created here rather than needing a separate path.
   *
   * <p><b>Always evaluates.</b> Re-deriving the verdict from whatever values the row already holds
   * would be cheaper, and is wrong: a target cleared and re-set months later would pair a fresh
   * target with a stale measurement and produce a confident, incorrect verdict. The only case where
   * stored values are used instead is the kill switch below, where the alternative is no update at
   * all.
   *
   * <p>Cost is one definition's worth of aggregations — two reports across four ranges, pinning a
   * single instance index — which is what makes this affordable inside a request.
   *
   * @param target the target exactly as persisted — passed in rather than re-read so that two
   *     concurrent saves for the same definition cannot each apply the other's value
   */
  public void computeRowsForTarget(final BusinessValueTargetDto target) {
    if (target == null) {
      throw new IllegalArgumentException("target must not be null");
    }

    // Evaluation is what overviewComputeEnabled exists to stop, so it is gated here too. Falling
    // back to the stored values keeps the target itself coherent at no query cost, which is the
    // behaviour the switch is meant to preserve: numbers freeze, the dashboard keeps working.
    if (!configurationService.getBusinessValueConfiguration().isOverviewComputeEnabled()) {
      LOG.debug(
          "Business-value overview compute is disabled; re-deriving the verdict for tenant [{}] "
              + "definition [{}] from stored values instead of evaluating",
          target.getTenantId(),
          target.getProcessDefinitionKey());
      applyTargetToExistingRows(target);
      return;
    }

    final DefinitionEntry entry =
        new DefinitionEntry(
            target.getTenantId(),
            target.getProcessDefinitionKey(),
            definitionName(target.getProcessDefinitionKey()));

    final List<BusinessValueOverviewDto> rows =
        rowsForTenant(
            target.getTenantId(),
            List.of(entry),
            List.of(MetricRange.values()),
            mappingMetadataRepository.getProcessDefinitionKeysWithInstanceIndex(),
            // Measured whatever the new target says, including a clear: the values are refreshed
            // one last time on the way out, and the sweep stops measuring it from the next tick.
            Set.of(targetDocId(target.getTenantId(), target.getProcessDefinitionKey())));

    final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    for (final BusinessValueOverviewDto row : rows) {
      row.setLastComputedAt(now);
      applyTarget(row, target);
    }
    overviewWriter.bulkUpsertFromTargetWrite(rows);
  }

  /**
   * Applies a target to the rows that already exist, without measuring anything. Used only when the
   * sweep is switched off — a definition with no rows yet gets nothing, and {@code
   * BusinessValueOverviewReadService} surfaces that case by joining live targets onto the rows it
   * reads.
   *
   * <p>{@link BusinessValueOverviewDto#getLastComputedAt()} is left as it was: it records when the
   * values were measured, and nothing was measured here.
   */
  private void applyTargetToExistingRows(final BusinessValueTargetDto target) {
    final List<BusinessValueOverviewDto> rows = new ArrayList<>();
    for (final MetricRange range : MetricRange.values()) {
      overviewRepository
          .getByKey(target.getTenantId(), target.getProcessDefinitionKey(), range)
          .ifPresent(
              row -> {
                applyTarget(row, target);
                rows.add(row);
              });
    }
    if (!rows.isEmpty()) {
      overviewWriter.bulkUpsertFromTargetWrite(rows);
    }
  }

  private String definitionName(final String processDefinitionKey) {
    return definitionService
        .getProcessDefinitionWithTenants(processDefinitionKey)
        .map(SimpleDefinitionDto::getName)
        .orElse(processDefinitionKey);
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
      final Set<String> targetedDocIds) {
    final List<List<DefinitionEntry>> chunks =
        chunk(evaluableDefinitions(tenantDefinitions, keysWithInstanceIndex, targetedDocIds));
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
                automationPctByKey.get(def.processDefinitionKey())));
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
      final List<DefinitionEntry> definitions,
      final Set<String> lowercasedKeysWithInstanceIndex,
      final Set<String> targetedDocIds) {
    return definitions.stream()
        .filter(
            def ->
                lowercasedKeysWithInstanceIndex.contains(
                    def.processDefinitionKey().toLowerCase(Locale.ENGLISH)))
        .filter(
            def -> targetedDocIds.contains(targetDocId(def.tenantId(), def.processDefinitionKey())))
        .toList();
  }

  /**
   * The {@code (tenantId, processDefinitionKey)} pairs worth measuring — those that currently carry
   * a target.
   *
   * <p>Keyed by the pair rather than by definition key alone. The same BPMN process id can be
   * deployed on several tenants and targeted on only one of them, and the Hub's target modal lets a
   * user pick that tenant, so a key-level set would measure a definition for tenants nobody asked
   * about and attribute one tenant's answer to another.
   *
   * <p>A cleared target leaves its document behind with null fields rather than deleting it, so
   * presence alone is not enough — the definition stops being measured only once every target field
   * is null, which is what clearing is supposed to mean.
   */
  private Set<String> targetedDocIds() {
    return readTargets().entrySet().stream()
        .filter(entry -> hasAnyTarget(entry.getValue()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  /**
   * Whether a target document actually carries a target. Clearing a target leaves the document
   * behind with every field null rather than deleting it, so presence in the index is not the same
   * as being targeted — the sweep uses this to decide what to measure, and {@code
   * BusinessValueOverviewReadService} to decide what is worth surfacing.
   */
  static boolean hasAnyTarget(final BusinessValueTargetDto target) {
    return target.getCycleTimeTargetMillis() != null || target.getAutomationRateTargetPct() != null;
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

  /**
   * Builds a row carrying its measured values and no target. {@link #applyTarget} fills in the
   * target and the verdict it implies once every evaluation has returned — see {@link
   * #computeOverviewRows} for why that happens last rather than here.
   */
  private BusinessValueOverviewDto buildRow(
      final DefinitionEntry def,
      final MetricRange range,
      final Double cycleMillis,
      final Double automationPct) {
    return new BusinessValueOverviewDto(
        def.tenantId(),
        def.processDefinitionKey(),
        def.processDefinitionName(),
        range,
        // Replaced with the write-time stamp by the caller; see computeOverviewRows.
        null,
        BusinessValueVerdict.cycleTimeBlock(toLongMillis(cycleMillis), null),
        BusinessValueVerdict.automationRateBlock(automationPct, null),
        false,
        0,
        0);
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
