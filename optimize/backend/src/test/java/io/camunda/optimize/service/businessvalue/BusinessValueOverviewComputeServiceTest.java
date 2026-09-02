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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessDefinitionKeyGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
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
import io.camunda.optimize.service.db.writer.BusinessValueOverviewWriter;
import io.camunda.optimize.service.report.ReportService;
<<<<<<< HEAD
=======
import io.camunda.optimize.service.util.configuration.BusinessValueConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
>>>>>>> 9076f22e (perf: measure only the process definitions that have a target)
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Guards the property the fan-in exists for: report evaluations per sweep scale with the number of
 * definition <em>chunks per tenant</em> rather than with the definition count. The per-tenant
 * qualifier matters — chunking happens inside the per-tenant loop, so the saving is proportional to
 * how many definitions a single tenant owns, not to catalog size.
 */
class BusinessValueOverviewComputeServiceTest {

  private static final String DEFAULT_TENANT = "<default>";

  private BusinessValueTargetRepository targetRepository;
  private BusinessValueOverviewRepository overviewRepository;
  private BusinessValueOverviewWriter overviewWriter;
  private DefinitionService definitionService;
  private ReportService reportService;
  private PlainReportEvaluationHandler reportEvaluationHandler;
  private MappingMetadataRepository mappingMetadataRepository;
  private BusinessValueOverviewComputeService computeService;

  /** Values the stubbed evaluation should return, keyed by process definition key. */
  private final Map<String, Double> stubbedValuesByKey = new HashMap<>();

  /**
   * Values keyed by tenant instead, for asserting that a tenant's answer lands only on that
   * tenant's rows. Takes precedence over {@link #stubbedValuesByKey} when populated.
   */
  private final Map<String, Double> valuesByTenant = new HashMap<>();

  /**
   * Bucket key the stub should emit for a given definition key, when it must differ from the pinned
   * key. Without this the stub echoes the pinned key back and the bucket-key to lookup-key mapping
   * cannot fail, whatever the production code does.
   */
  private final Map<String, String> bucketKeyOverrides = new HashMap<>();

  private final AtomicInteger evaluationCount = new AtomicInteger();

  @BeforeEach
  void setUp() {
    targetRepository = mock(BusinessValueTargetRepository.class);
    overviewRepository = mock(BusinessValueOverviewRepository.class);
    overviewWriter = mock(BusinessValueOverviewWriter.class);
    definitionService = mock(DefinitionService.class);
    reportService = mock(ReportService.class);
    reportEvaluationHandler = mock(PlainReportEvaluationHandler.class);
    mappingMetadataRepository = mock(MappingMetadataRepository.class);

    when(targetRepository.scanAll()).thenReturn(List.of());
    when(reportService.getReportDefinition(anyString())).thenAnswer(invocation -> seededReport());

    stubEvaluation();

    computeService =
        new BusinessValueOverviewComputeService(
            targetRepository,
            overviewRepository,
            overviewWriter,
            definitionService,
            reportService,
            reportEvaluationHandler,
            mappingMetadataRepository,
            searchLimitsWithBucketLimit(1000));
  }

  private void stubEvaluation() {
    when(reportEvaluationHandler.evaluateReport(any(ReportEvaluationInfo.class)))
        .thenAnswer(
            invocation -> {
              evaluationCount.incrementAndGet();
              final ReportEvaluationInfo info = invocation.getArgument(0);
              final ProcessReportDataDto reportData =
                  (ProcessReportDataDto) info.getReport().getData();
              return evaluationResultFor(reportData);
            });
  }

  /**
   * The sweep must take its target snapshot after the last evaluation returns, not before the
   * first. Evaluating a whole catalog takes long enough for a user to save a target in the middle
   * of it, and the upsert has no version precondition — so a snapshot read up front means the sweep
   * writes back the target as it was before the save, silently reverting it until the next tick.
   *
   * <p>Asserted as an ordering rather than a value because a test that only checked the written row
   * would pass on a sweep that read early and got lucky on timing.
   */
  @Test
  void shouldReadTargetsAfterEvaluatingAndImmediatelyBeforeWriting() {
    // given a catalog to sweep
    givenDefinitions(DEFAULT_TENANT, 2);

    // when a sweep runs
    computeService.computeOverviewRows(allRanges());

    // then targets are read only once every evaluation has returned, and the write follows
    final InOrder inOrder = inOrder(reportEvaluationHandler, targetRepository, overviewWriter);
    inOrder.verify(reportEvaluationHandler, atLeastOnce()).evaluateReport(any());
    inOrder.verify(targetRepository).scanAll();
    inOrder.verify(overviewWriter).bulkUpsertFromScheduler(any());
    inOrder.verifyNoMoreInteractions();
  }

  /**
   * The measured value belongs to the row that was just evaluated; the target is applied on top of
   * it afterwards. This pins that the late target read still lands on the right row rather than
   * being dropped by the reordering.
   */
  @Test
  void shouldApplyTheTargetItReadToTheRowItEvaluated() {
    // given one definition measured at 5s against a 1s cycle-time target, and no automation target
    givenDefinitions(DEFAULT_TENANT, 1);
    final String key = "process-0";
    stubbedValuesByKey.put(key, 5_000d);
    when(targetRepository.scanAll())
        .thenReturn(List.of(cycleTimeTarget(DEFAULT_TENANT, key, 1_000L)));

    // when a sweep runs for a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the written row pairs the evaluated value with the target and the verdict it implies
    final BusinessValueOverviewDto row = capturedRows().getFirst();
    assertThat(row.getCycleTime().getValue()).isEqualTo(5_000L);
    assertThat(row.getCycleTime().getTarget()).isEqualTo(1_000L);
    assertThat(row.getCycleTime().getMet()).isFalse();
    assertThat(row.getAutomationRate().getTarget()).isNull();
    assertThat(row.getTargetsSet()).isEqualTo(1);
    assertThat(row.getTargetsMet()).isZero();
    assertThat(row.isHasAnyTarget()).isTrue();
  }

  /**
   * A sweep that began before a target write and finished after it would otherwise stamp the row
   * with its own start time, moving the row's freshness backwards — and the stale-read backstop
   * keys off exactly that field. Stamping at write time makes the last writer's stamp the latest.
   *
   * <p>Asserted against the moment evaluation actually ran, because a stamp taken at the start of
   * the sweep would sit before it and one taken at the end after it. Comparing to "now" instead
   * would pass either way.
   */
  @Test
  void shouldStampFreshnessWhenWritingRatherThanWhenTheSweepStarted() {
    // given a definition, recording when its evaluation ran
    givenDefinitions(DEFAULT_TENANT, 1);
    final AtomicReference<OffsetDateTime> evaluatedAt = new AtomicReference<>();
    when(reportEvaluationHandler.evaluateReport(any(ReportEvaluationInfo.class)))
        .thenAnswer(
            invocation -> {
              evaluationCount.incrementAndGet();
              evaluatedAt.set(OffsetDateTime.now(ZoneOffset.UTC));
              return evaluationResultFor(
                  (ProcessReportDataDto)
                      ((ReportEvaluationInfo) invocation.getArgument(0)).getReport().getData());
            });

    // when a sweep runs
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the row was stamped after the measurement, not before it
    assertThat(evaluatedAt.get()).isNotNull();
    assertThat(capturedRows())
        .isNotEmpty()
        .allSatisfy(row -> assertThat(row.getLastComputedAt()).isAfterOrEqualTo(evaluatedAt.get()));
  }

  // --- the sweep measures only what someone asked for ---

  /**
   * Measuring the whole catalogue costs one instance-index scan per definition per range, and most
   * of that is thrown away: without a target there is no verdict to compute, and L0 shows nothing
   * about the definition beyond its presence in the coverage denominator.
   */
  @Test
  void shouldMeasureOnlyDefinitionsThatHaveATarget() {
    // given ten definitions, two of which carry a target
    givenDefinitions(DEFAULT_TENANT, 10);
    when(targetRepository.scanAll())
        .thenReturn(
            List.of(
                cycleTimeTarget(DEFAULT_TENANT, "process-2", 1_000L),
                cycleTimeTarget(DEFAULT_TENANT, "process-7", 1_000L)));

    // when a sweep runs for one range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then only those two are pinned onto the evaluation
    assertThat(capturedChunkKeys())
        .allSatisfy(keys -> assertThat(keys).containsExactlyInAnyOrder("process-2", "process-7"));
  }

  /**
   * Every definition still gets a row, measured or not. The overview's coverage denominator is the
   * row count, so dropping untargeted rows would make coverage read "n of n" — always 100%.
   */
  @Test
  void shouldStillWriteARowForEveryDefinitionItDidNotMeasure() {
    // given ten definitions, one targeted
    givenDefinitions(DEFAULT_TENANT, 10);
    when(targetRepository.scanAll())
        .thenReturn(List.of(cycleTimeTarget(DEFAULT_TENANT, "process-2", 1_000L)));

    // when a sweep runs for one range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then all ten rows are written, and only the targeted one carries a target
    final List<BusinessValueOverviewDto> rows = capturedRows();
    assertThat(rows).hasSize(10);
    assertThat(rows.stream().filter(BusinessValueOverviewDto::isHasAnyTarget).toList())
        .singleElement()
        .satisfies(row -> assertThat(row.getProcessDefinitionKey()).isEqualTo("process-2"));
  }

  /**
   * The same BPMN process id can be deployed on several tenants and targeted on only one of them —
   * the Hub's target modal lets a user pick the tenant. Filtering on the definition key alone would
   * measure it for tenants nobody asked about and blend one tenant's answer into another's row.
   */
  @Test
  void shouldMeasureAKeyOnlyForTheTenantItIsTargetedOn() {
    // given one process key deployed on two tenants, targeted on one
    final String sharedKey = "shared-process";
    final DefinitionWithTenantIdsDto definition =
        new DefinitionWithTenantIdsDto(
            sharedKey,
            sharedKey,
            DefinitionType.PROCESS,
            new ArrayList<>(List.of(DEFAULT_TENANT, "tenant-b")),
            Collections.emptySet());
    when(definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS))
        .thenReturn(List.of(definition));
    when(mappingMetadataRepository.getProcessDefinitionKeysWithInstanceIndex())
        .thenReturn(Set.of(sharedKey));
    when(targetRepository.scanAll())
        .thenReturn(List.of(cycleTimeTarget("tenant-b", sharedKey, 1_000L)));
    // both tenants have a value to return, so an untargeted tenant that got measured anyway would
    // show it rather than staying null — without this the assertion below passes with no filter
    valuesByTenant.put("tenant-b", 4_000d);
    valuesByTenant.put(DEFAULT_TENANT, 9_000d);

    // when a sweep runs for one range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then only the targeted tenant's row is measured; the other keeps a null value
    assertThat(capturedRows())
        .filteredOn(row -> "tenant-b".equals(row.getTenantId()))
        .singleElement()
        .satisfies(row -> assertThat(row.getCycleTime().getValue()).isEqualTo(4_000L));
    assertThat(capturedRows())
        .filteredOn(row -> DEFAULT_TENANT.equals(row.getTenantId()))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getCycleTime().getValue()).isNull();
              assertThat(row.isHasAnyTarget()).isFalse();
            });
  }

  /**
   * Clearing a target leaves its document behind with null fields rather than deleting it, so
   * presence in the target index is not the same as being targeted.
   */
  @Test
  void shouldStopMeasuringADefinitionWhoseTargetHasBeenCleared() {
    // given a definition whose target document exists but holds no target values
    givenDefinitions(DEFAULT_TENANT, 1);
    when(targetRepository.scanAll())
        .thenReturn(
            List.of(
                new BusinessValueTargetDto(
                    "process-0",
                    DEFAULT_TENANT,
                    null,
                    null,
                    null,
                    OffsetDateTime.now(),
                    "someone")));

    // when a sweep runs
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then nothing is measured, though the row is still written
    assertThat(evaluationCount.get()).isZero();
    assertThat(capturedRows()).hasSize(1);
  }

  /**
   * A target saved while a sweep is running was not in the sweep's selection snapshot, so the sweep
   * never measured it and the row it built carries null values. The save has already measured the
   * definition and written real ones. Writing the sweep's row anyway would replace them with nulls
   * and stamp it fresh, so the backstop would not heal it either — the user would see "target set,
   * no data" until the next tick, which is what this whole change exists to prevent.
   */
  @Test
  void shouldNotOverwriteAFreshlyMeasuredRowForADefinitionTargetedMidSweep() {
    // given two definitions, only one targeted when the sweep selects its work
    givenDefinitions(DEFAULT_TENANT, 2);
    final BusinessValueTargetDto alreadyTargeted =
        cycleTimeTarget(DEFAULT_TENANT, "process-0", 1_000L);
    final BusinessValueTargetDto targetedMidSweep =
        cycleTimeTarget(DEFAULT_TENANT, "process-1", 1_000L);
    // the selection read sees one target; the read before the write sees both, as it would if the
    // second were saved while the evaluations were still running
    when(targetRepository.scanAll())
        .thenReturn(List.of(alreadyTargeted))
        .thenReturn(List.of(alreadyTargeted, targetedMidSweep));

    // when a sweep runs
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the mid-sweep definition's row is left alone rather than written back with null values
    assertThat(capturedRows())
        .extracting(BusinessValueOverviewDto::getProcessDefinitionKey)
        .containsExactly("process-0")
        .doesNotContain("process-1");
  }

  /**
   * A target cleared mid-sweep still has its verdict cleared — only the reverse case is skipped.
   */
  @Test
  void shouldStillWriteARowForADefinitionWhoseTargetWasClearedMidSweep() {
    // given a targeted definition whose target is cleared while the sweep runs
    givenDefinitions(DEFAULT_TENANT, 1);
    when(targetRepository.scanAll())
        .thenReturn(List.of(cycleTimeTarget(DEFAULT_TENANT, "process-0", 1_000L)))
        .thenReturn(
            List.of(
                new BusinessValueTargetDto(
                    "process-0",
                    DEFAULT_TENANT,
                    null,
                    null,
                    null,
                    OffsetDateTime.now(),
                    "someone")));

    // when a sweep runs
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the row is written with the target and verdict cleared
    assertThat(capturedRows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getProcessDefinitionKey()).isEqualTo("process-0");
              assertThat(row.isHasAnyTarget()).isFalse();
              assertThat(row.getCycleTime().getTarget()).isNull();
            });
  }

  // --- computeRowsForTarget: the target-write path ---

  /**
   * Without this the saved target does not reach L0 until the next sweep: the row carries the
   * target it was last computed with, and the stale-read backstop keys off the age of the
   * measurement, so a freshly measured row holding a stale target looks perfectly fresh to it.
   */
  @Test
  void shouldMeasureAndWriteEveryRangeWhenATargetIsSaved() {
    // given a definition with instances, measured at 5s, and a 1s cycle-time target
    givenDefinitions(DEFAULT_TENANT, 1);
    stubbedValuesByKey.put("process-0", 5_000d);

    // when the target is saved
    computeService.computeRowsForTarget(cycleTimeTarget(DEFAULT_TENANT, "process-0", 1_000L));

    // then one row per range is written, each pairing a fresh measurement with the new target
    final List<BusinessValueOverviewDto> written = capturedTargetWriteRows();
    assertThat(written)
        .extracting(BusinessValueOverviewDto::getMetricRange)
        .containsExactlyInAnyOrder(MetricRange.values());
    assertThat(written)
        .allSatisfy(
            row -> {
              assertThat(row.getCycleTime().getValue()).isEqualTo(5_000L);
              assertThat(row.getCycleTime().getTarget()).isEqualTo(1_000L);
              assertThat(row.getCycleTime().getMet()).isFalse();
              assertThat(row.isHasAnyTarget()).isTrue();
              assertThat(row.getTargetsSet()).isEqualTo(1);
            });
  }

  /**
   * Only the definition whose target changed is measured. Sweeping the catalogue on every modal
   * save is the cost this path exists to avoid.
   */
  @Test
  void shouldMeasureOnlyTheDefinitionWhoseTargetWasSaved() {
    // given a catalogue of ten definitions
    givenDefinitions(DEFAULT_TENANT, 10);

    // when a target is saved on one of them
    computeService.computeRowsForTarget(cycleTimeTarget(DEFAULT_TENANT, "process-3", 1_000L));

    // then only that definition is pinned, and the cost is two reports x four ranges x one chunk
    assertThat(capturedChunkKeys())
        .allSatisfy(keys -> assertThat(keys).containsExactly("process-3"));
    assertThat(evaluationCount.get()).isEqualTo(2 * 4 * 1);
    assertThat(capturedTargetWriteRows())
        .extracting(BusinessValueOverviewDto::getProcessDefinitionKey)
        .containsOnly("process-3");
  }

  /**
   * A definition imported since the last sweep has no rows at all. Measuring on save is what
   * creates them — otherwise setting a target on a fresh import shows nothing until the next tick.
   */
  @Test
  void shouldCreateRowsForADefinitionThatHasNoneYet() {
    // given a definition the sweep has never written a row for
    givenDefinitions(DEFAULT_TENANT, 1);
    when(overviewRepository.getByKey(anyString(), anyString(), any())).thenReturn(Optional.empty());

    // when a target is saved
    computeService.computeRowsForTarget(cycleTimeTarget(DEFAULT_TENANT, "process-0", 1_000L));

    // then the rows are created rather than skipped
    assertThat(capturedTargetWriteRows()).hasSize(MetricRange.values().length);
  }

  /**
   * Re-deriving from stored values would be cheaper and is the tempting optimization. It is also
   * the bug: a target cleared and re-set later would be judged against whatever measurement the row
   * still held, which could be months old.
   */
  @Test
  void shouldMeasureAgainEvenWhenTheRowAlreadyHasValues() {
    // given a definition whose rows already carry measurements
    givenDefinitions(DEFAULT_TENANT, 1);
    stubbedValuesByKey.put("process-0", 5_000d);
    givenExistingRowsForAllRanges(DEFAULT_TENANT, "process-0", 999_999L, 1d);

    // when a target is saved
    computeService.computeRowsForTarget(cycleTimeTarget(DEFAULT_TENANT, "process-0", 1_000L));

    // then the value written is freshly measured, not the one already on the row
    assertThat(evaluationCount.get()).isPositive();
    assertThat(capturedTargetWriteRows())
        .allSatisfy(row -> assertThat(row.getCycleTime().getValue()).isEqualTo(5_000L));
  }

  /** Clearing a target has to clear the verdict with it, not leave the last one standing. */
  @Test
  void shouldClearTheVerdictWhenTheTargetIsCleared() {
    // given a measured definition
    givenDefinitions(DEFAULT_TENANT, 1);
    stubbedValuesByKey.put("process-0", 5_000d);

    // when the target is cleared
    computeService.computeRowsForTarget(
        new BusinessValueTargetDto(
            "process-0", DEFAULT_TENANT, null, null, null, OffsetDateTime.now(), "someone"));

    // then the rows keep their measurements but carry no target, verdict or counters
    assertThat(capturedTargetWriteRows())
        .allSatisfy(
            row -> {
              assertThat(row.getCycleTime().getValue()).isEqualTo(5_000L);
              assertThat(row.getCycleTime().getTarget()).isNull();
              assertThat(row.getCycleTime().getMet()).isNull();
              assertThat(row.isHasAnyTarget()).isFalse();
              assertThat(row.getTargetsSet()).isZero();
              assertThat(row.getTargetsMet()).isZero();
            });
  }

  @Test
  void shouldEvaluateOncePerReportRangeAndChunkRatherThanPerDefinition() {
    // given definitions that fit a single chunk
    computeService = computeServiceWith(searchLimitsWithBucketLimit(50));
    givenDefinitions(DEFAULT_TENANT, 50);

    // when computing across all four range presets
    computeService.computeOverviewRows(allRanges());

    // then two reports x four ranges x one chunk — 50 definitions cost 8 evaluations, not 400
    assertThat(evaluationCount.get()).isEqualTo(2 * 4 * 1);
  }

  @Test
  void shouldScaleEvaluationsWithChunkCountNotDefinitionCount() {
    // given a chunk size of 50 and a catalog of 100 definitions on one tenant
    computeService = computeServiceWith(searchLimitsWithBucketLimit(50));
    givenDefinitions(DEFAULT_TENANT, 100);
    computeService.computeOverviewRows(allRanges());
    final int evaluationsForOneHundred = evaluationCount.getAndSet(0);

    // when the catalog doubles
    reset(overviewWriter);
    givenDefinitions(DEFAULT_TENANT, 200);
    computeService.computeOverviewRows(allRanges());
    final int evaluationsForTwoHundred = evaluationCount.get();

    // then the unit of growth is the chunk, not the definition: doubling the catalog doubles the
    // chunk count, where a per-definition sweep would have issued 800 and 1600 evaluations
    assertThat(evaluationsForOneHundred).isEqualTo(2 * 4 * 2);
    assertThat(evaluationsForTwoHundred).isEqualTo(2 * 4 * 4);
  }

  /**
   * The chunk is bounded by request-line length as well as by bucket count. Index names travel in
   * the URL, so long BPMN process ids split a chunk that the bucket limit alone would have allowed
   * through — and an over-long request line fails with a 400 that has no retry path.
   */
  @Test
  void shouldSplitOnRequestLineLengthEvenWhenTheBucketLimitAllowsMore() {
    // given few enough definitions for the bucket limit, but very long keys
    computeService = computeServiceWith(searchLimitsWithBucketLimit(1000));
    final String longKey = "a".repeat(200);
    givenDefinitionKeys(DEFAULT_TENANT, IntStream.range(0, 40).mapToObj(i -> longKey + i).toList());

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the byte budget forces a split the bucket limit would not have
    assertThat(capturedChunkKeys()).hasSizeGreaterThan(2);
    assertThat(capturedChunkKeys().stream().flatMap(List::stream).distinct().toList()).hasSize(40);
  }

  /**
   * Asserts chunk <em>membership</em>, not just the evaluation count. Counting alone is passable by
   * a broken split: an off-by-one that overlapped or skipped a definition would still produce two
   * chunks and the same number of calls.
   */
  @Test
  void shouldSplitDefinitionsAcrossChunksWithoutLossOrOverlap() {
    // given a bucket limit that forces a split at a known boundary
    computeService = computeServiceWith(searchLimitsWithBucketLimit(10));
    givenDefinitions(DEFAULT_TENANT, 25);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then every definition is pinned exactly once, in chunks of the configured size
    final List<List<String>> chunksPerEvaluation = capturedChunkKeys();
    assertThat(chunksPerEvaluation).hasSize(3 * 2);
    assertThat(chunksPerEvaluation.stream().map(List::size).distinct().toList())
        .containsExactlyInAnyOrder(10, 5);

    final List<String> firstReportChunks =
        chunksPerEvaluation.stream().limit(3).flatMap(List::stream).toList();
    assertThat(firstReportChunks)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrderElementsOf(
            IntStream.range(0, 25).mapToObj(i -> "process-" + i).toList());
  }

  /**
   * Non-ASCII process ids cost far more on the wire than they do in characters, and the
   * request-line limit counts bytes. {@code ü} is one character but arrives percent-encoded as
   * {@code %C3%BC} — six bytes — and a CJK character costs nine. Budgeting by character length
   * would let a chunk of such keys through and overrun the limit, which fails the request with a
   * 400 that has no retry path.
   */
  @Test
  void shouldChargeNonAsciiKeysTheirEncodedByteCost() {
    // given two catalogs of identical character length, one ASCII and one CJK
    computeService = computeServiceWith(searchLimitsWithBucketLimit(1000));
    final int keyLength = 30;
    final int definitionCount = 60;

    givenDefinitionKeys(
        DEFAULT_TENANT,
        IntStream.range(0, definitionCount).mapToObj(i -> "a".repeat(keyLength) + i).toList());
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));
    final int asciiChunks = capturedChunkKeys().size();

    reset(reportEvaluationHandler, overviewWriter);
    stubEvaluation();
    computeService = computeServiceWith(searchLimitsWithBucketLimit(1000));
    givenDefinitionKeys(
        DEFAULT_TENANT,
        IntStream.range(0, definitionCount).mapToObj(i -> "\u8acb".repeat(keyLength) + i).toList());

    // when computing a single range over each
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));
    final int cjkChunks = capturedChunkKeys().size();

    // then the CJK catalog is split far more aggressively despite the same character count
    assertThat(cjkChunks).isGreaterThan(asciiChunks);
  }

  /**
   * A degenerate bucket limit must not be able to stall the sweep. The chunk loop only flushes a
   * non-empty chunk, so it always makes progress — without that, a limit of 0 would append empty
   * chunks forever and take the feature out until the process restarts, since the scheduler runs on
   * a single-thread fixed-delay pool.
   */
  @Test
  void shouldStillMakeProgressWhenTheBucketLimitIsDegenerate() {
    // given a bucket limit of zero
    computeService = computeServiceWith(searchLimitsWithBucketLimit(0));
    givenDefinitions(DEFAULT_TENANT, 3);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then it terminates, one definition per chunk, and every definition still gets a row
    assertThat(capturedChunkKeys()).allSatisfy(chunk -> assertThat(chunk).hasSize(1));
    assertThat(capturedRows()).hasSize(3);
  }

  /**
   * Values are stubbed only for a definition in the <em>last</em> chunk, so a merge that kept just
   * the first chunk's results — or overwrote instead of accumulating — would null this row.
   */
  @Test
  void shouldKeepValuesFromEveryChunkNotJustTheFirst() {
    // given three chunks' worth of definitions and a value only in the final chunk
    computeService = computeServiceWith(searchLimitsWithBucketLimit(10));
    givenDefinitions(DEFAULT_TENANT, 25);
    stubbedValuesByKey.put("process-0", 1_111.0);
    stubbedValuesByKey.put("process-24", 4_242.0);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then values from the first and last chunk both survive — asserting both directions catches a
    // merge that keeps only one chunk, whichever one it keeps
    final List<BusinessValueOverviewDto> rows = capturedRows();
    assertThat(rows)
        .filteredOn(row -> "process-0".equals(row.getProcessDefinitionKey()))
        .singleElement()
        .satisfies(row -> assertThat(row.getCycleTime().getValue()).isEqualTo(1_111L));
    assertThat(rows)
        .filteredOn(row -> "process-24".equals(row.getProcessDefinitionKey()))
        .singleElement()
        .satisfies(row -> assertThat(row.getCycleTime().getValue()).isEqualTo(4_242L));
  }

  @Test
  void shouldEvaluateSeparatelyForEachTenant() {
    // given the same process key deployed on two tenants
    givenDefinitionsAcrossTenants(Map.of("tenant-a", 1, "tenant-b", 1));

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then each tenant gets its own evaluation pair — the reports group by definition key only and
    // carry no tenant dimension, so one evaluation cannot serve two tenants
    assertThat(evaluationCount.get()).isEqualTo(2 * 2);
  }

  /**
   * Counting evaluations proves the tenants are queried separately; this proves the answers are not
   * crossed over on the way back into the rows.
   */
  @Test
  void shouldAttributeEachTenantsValueToItsOwnRow() {
    // given one process key on two tenants, each measuring a different cycle time
    givenDefinitionsAcrossTenants(Map.of("tenant-a", 1, "tenant-b", 1));
    valuesByTenant.put("tenant-a", 1_000.0);
    valuesByTenant.put("tenant-b", 9_000.0);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then neither tenant sees the other's number, nor the blend of the two
    final List<BusinessValueOverviewDto> rows = capturedRows();
    assertThat(rows)
        .filteredOn(row -> "tenant-a".equals(row.getTenantId()))
        .singleElement()
        .satisfies(row -> assertThat(row.getCycleTime().getValue()).isEqualTo(1_000L));
    assertThat(rows)
        .filteredOn(row -> "tenant-b".equals(row.getTenantId()))
        .singleElement()
        .satisfies(row -> assertThat(row.getCycleTime().getValue()).isEqualTo(9_000L));
  }

  @Test
  void shouldSkipDefinitionsWhoseTenantIsNotDefined() {
    // given a definition surfacing the "not defined" tenant bucket as a literal null alongside a
    // real tenant, as DefinitionReader does for legacy or malformed rows
    final DefinitionWithTenantIdsDto definition =
        new DefinitionWithTenantIdsDto(
            "process-0",
            "process-0",
            DefinitionType.PROCESS,
            new ArrayList<>(Arrays.asList(null, DEFAULT_TENANT)),
            Collections.emptySet());
    when(definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS))
        .thenReturn(List.of(definition));
    when(mappingMetadataRepository.getProcessDefinitionKeysWithInstanceIndex())
        .thenReturn(Set.of("process-0"));

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then only the real tenant gets a row — the writer rejects a null tenantId, and one
    // un-tenanted definition must not abort the whole sweep
    assertThat(capturedRows())
        .singleElement()
        .satisfies(row -> assertThat(row.getTenantId()).isEqualTo(DEFAULT_TENANT));
  }

  @Test
  void shouldPinEveryDefinitionInTheChunkToTheEvaluatedTenant() {
    // given two definitions on one tenant
    givenDefinitions("tenant-a", 2);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the evaluated report carries both definitions, each pinned to that tenant, and the
    // business-value flag is cleared so the handler does not replace them with the whole catalog
    final ArgumentCaptor<ReportEvaluationInfo> captor =
        ArgumentCaptor.forClass(ReportEvaluationInfo.class);
    verify(reportEvaluationHandler, org.mockito.Mockito.atLeastOnce())
        .evaluateReport(captor.capture());

    final ProcessReportDataDto reportData =
        (ProcessReportDataDto) captor.getValue().getReport().getData();
    assertThat(reportData.isBusinessValueReport()).isFalse();
    assertThat(reportData.getDefinitions())
        .hasSize(2)
        .allSatisfy(
            definition -> assertThat(definition.getTenantIds()).containsExactly("tenant-a"));
    assertThat(reportData.getDefinitions())
        .extracting(ReportDataDefinitionDto::getKey)
        .containsExactlyInAnyOrder("process-0", "process-1");
  }

  @Test
  void shouldWriteEachDefinitionItsOwnValueFromTheSharedEvaluation() {
    // given two definitions on one tenant with different measured values
    givenDefinitions(DEFAULT_TENANT, 2);
    stubbedValuesByKey.put("process-0", 1000.0);
    stubbedValuesByKey.put("process-1", 5000.0);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then each row carries its own definition's value out of the shared result map
    final List<BusinessValueOverviewDto> rows = capturedRows();
    assertThat(rows)
        .filteredOn(row -> "process-0".equals(row.getProcessDefinitionKey()))
        .singleElement()
        .satisfies(row -> assertThat(row.getCycleTime().getValue()).isEqualTo(1000L));
    assertThat(rows)
        .filteredOn(row -> "process-1".equals(row.getProcessDefinitionKey()))
        .singleElement()
        .satisfies(row -> assertThat(row.getCycleTime().getValue()).isEqualTo(5000L));
  }

  @Test
  void shouldStillWriteARowForADefinitionWithNoInstanceIndex() {
    // given two definitions of which only one has ever had an instance imported
    givenDefinitions(DEFAULT_TENANT, 2);
    when(mappingMetadataRepository.getProcessDefinitionKeysWithInstanceIndex())
        .thenReturn(Set.of("process-0"));
    stubbedValuesByKey.put("process-0", 1000.0);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the definition without an index is kept out of the query — naming a missing index would
    // fail the whole request — but still gets a row, with null values
    final List<BusinessValueOverviewDto> rows = capturedRows();
    assertThat(rows).hasSize(2);
    assertThat(rows)
        .filteredOn(row -> "process-1".equals(row.getProcessDefinitionKey()))
        .singleElement()
        .satisfies(row -> assertThat(row.getCycleTime().getValue()).isNull());

    final ArgumentCaptor<ReportEvaluationInfo> captor =
        ArgumentCaptor.forClass(ReportEvaluationInfo.class);
    verify(reportEvaluationHandler, org.mockito.Mockito.atLeastOnce())
        .evaluateReport(captor.capture());
    final ProcessReportDataDto reportData =
        (ProcessReportDataDto) captor.getValue().getReport().getData();
    assertThat(reportData.getDefinitions())
        .extracting(ReportDataDefinitionDto::getKey)
        .containsExactly("process-0");
  }

  /**
   * The instance index name is built by lowercasing the definition key, so the identifiers read
   * back from the index pattern are lowercase while the definition keeps the casing of its BPMN
   * process id. Comparing the two directly would drop every mixed-case key — {@code Process_1} is
   * the Modeler default — out of the query and leave its row permanently null.
   */
  @Test
  void shouldEvaluateDefinitionsWhoseKeyContainsUppercase() {
    // given a definition whose BPMN process id is mixed case, whose instance index is therefore
    // registered in lowercase
    final DefinitionWithTenantIdsDto definition =
        new DefinitionWithTenantIdsDto(
            "Process_1",
            "Process_1",
            DefinitionType.PROCESS,
            new ArrayList<>(List.of(DEFAULT_TENANT)),
            Collections.emptySet());
    when(definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS))
        .thenReturn(List.of(definition));
    when(mappingMetadataRepository.getProcessDefinitionKeysWithInstanceIndex())
        .thenReturn(Set.of("process_1"));
    givenTargetsFor(List.of(definition));
    stubbedValuesByKey.put("Process_1", 7_000.0);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then it is still evaluated and its row carries the measured value
    assertThat(evaluationCount.get()).isEqualTo(2);
    assertThat(capturedRows())
        .singleElement()
        .satisfies(row -> assertThat(row.getCycleTime().getValue()).isEqualTo(7_000L));
  }

  /**
   * The index filter is case-insensitive; the value lookup is not, and must not become so. Bucket
   * keys are raw {@code processDefinitionKey} term values, so they match the definition exactly —
   * if the lookup ever normalized case it would silently pair a value with the wrong definition on
   * a catalog holding both {@code Order} and {@code order}.
   */
  @Test
  void shouldMatchValuesToDefinitionsOnTheExactBucketKey() {
    // given a bucket returned under a differently-cased key than the definition carries
    givenDefinitionKeys(DEFAULT_TENANT, List.of("Process_1"));
    bucketKeyOverrides.put("Process_1", "process_1");
    stubbedValuesByKey.put("Process_1", 5_000.0);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the value is not claimed by the mismatched definition
    assertThat(capturedRows())
        .singleElement()
        .satisfies(row -> assertThat(row.getCycleTime().getValue()).isNull());
  }

  @Test
  void shouldNotEvaluateWhenNoDefinitionHasAnInstanceIndex() {
    // given definitions that have never run
    givenDefinitions(DEFAULT_TENANT, 2);
    when(mappingMetadataRepository.getProcessDefinitionKeysWithInstanceIndex())
        .thenReturn(Set.of());

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then no query is issued at all, and both definitions still get a row
    verify(reportEvaluationHandler, never()).evaluateReport(any());
    assertThat(capturedRows()).hasSize(2);
  }

  @Test
  void shouldChunkBelowALoweredAggregationBucketLimit() {
    // given a bucket limit smaller than the default chunk size
    computeService =
        new BusinessValueOverviewComputeService(
            targetRepository,
            overviewWriter,
            definitionService,
            reportService,
            reportEvaluationHandler,
            mappingMetadataRepository,
            searchLimitsWithBucketLimit(10));
    givenDefinitions(DEFAULT_TENANT, 20);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the chunk follows the lowered limit rather than the default — a chunk larger than the
    // bucket limit would silently drop the definitions past it
    assertThat(evaluationCount.get()).isEqualTo(2 * 2);
  }

  private BusinessValueOverviewComputeService computeServiceWith(
      final SearchLimitsRepository searchLimits) {
    return new BusinessValueOverviewComputeService(
        targetRepository,
        overviewRepository,
        overviewWriter,
        definitionService,
        reportService,
        reportEvaluationHandler,
        mappingMetadataRepository,
        searchLimits);
  }

  /** The definition keys pinned onto each evaluation, in invocation order. */
  private List<List<String>> capturedChunkKeys() {
    final ArgumentCaptor<ReportEvaluationInfo> captor =
        ArgumentCaptor.forClass(ReportEvaluationInfo.class);
    verify(reportEvaluationHandler, atLeastOnce()).evaluateReport(captor.capture());
    return captor.getAllValues().stream()
        .map(info -> (ProcessReportDataDto) info.getReport().getData())
        .map(data -> data.getDefinitions().stream().map(ReportDataDefinitionDto::getKey).toList())
        .toList();
  }

  private void givenDefinitions(final String tenantId, final int count) {
    givenDefinitionsAcrossTenants(Map.of(tenantId, count));
  }

  private void givenDefinitionKeys(final String tenantId, final List<String> keys) {
    final List<DefinitionWithTenantIdsDto> definitions =
        keys.stream()
            .map(
                key ->
                    new DefinitionWithTenantIdsDto(
                        key,
                        key,
                        DefinitionType.PROCESS,
                        new ArrayList<>(List.of(tenantId)),
                        Collections.emptySet()))
            .toList();
    when(definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS))
        .thenReturn(definitions);
    when(mappingMetadataRepository.getProcessDefinitionKeysWithInstanceIndex())
        .thenReturn(
            keys.stream().map(k -> k.toLowerCase(Locale.ENGLISH)).collect(Collectors.toSet()));
    givenTargetsFor(definitions);
  }

  private void givenDefinitionsAcrossTenants(final Map<String, Integer> countsByTenant) {
    final List<DefinitionWithTenantIdsDto> definitions = new ArrayList<>();
    final Set<String> keys = new HashSet<>();
    countsByTenant.forEach(
        (tenantId, count) ->
            IntStream.range(0, count)
                .forEach(
                    index -> {
                      final String key = "process-" + index;
                      keys.add(key);
                      definitions.add(
                          new DefinitionWithTenantIdsDto(
                              key,
                              key,
                              DefinitionType.PROCESS,
                              // getTenantIds() sorts in place, so this list has to be mutable
                              new ArrayList<>(List.of(tenantId)),
                              Collections.emptySet()));
                    }));
    when(definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS))
        .thenReturn(definitions);
    when(mappingMetadataRepository.getProcessDefinitionKeysWithInstanceIndex()).thenReturn(keys);
    givenTargetsFor(definitions);
  }

  /**
   * Targets every definition the fixture created. The sweep only measures targeted definitions, so
   * without this the chunking and fan-in tests would all be asserting on an empty evaluation set.
   * Tests that care about the target filter itself override {@code targetRepository.scanAll()}.
   */
  private void givenTargetsFor(final List<DefinitionWithTenantIdsDto> definitions) {
    final List<BusinessValueTargetDto> targets = new ArrayList<>();
    for (final DefinitionWithTenantIdsDto def : definitions) {
      for (final String tenantId : def.getTenantIds()) {
        targets.add(cycleTimeTarget(tenantId, def.getKey(), 1_000L));
      }
    }
    when(targetRepository.scanAll()).thenReturn(targets);
  }

  private static BusinessValueTargetDto cycleTimeTarget(
      final String tenantId, final String processDefinitionKey, final long cycleTimeMillis) {
    return new BusinessValueTargetDto(
        processDefinitionKey,
        tenantId,
        cycleTimeMillis,
        TargetValueUnit.MILLIS,
        null,
        OffsetDateTime.now(),
        "someone");
  }

  private void givenExistingRowsForAllRanges(
      final String tenantId,
      final String processDefinitionKey,
      final Long cycleMillis,
      final Double automationPct) {
    when(overviewRepository.getByKey(eq(tenantId), eq(processDefinitionKey), any()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    new BusinessValueOverviewDto(
                        tenantId,
                        processDefinitionKey,
                        processDefinitionKey,
                        invocation.getArgument(2),
                        OffsetDateTime.now(),
                        new BusinessValueOverviewDto.CycleTimeBlock(cycleMillis, null, null),
                        new BusinessValueOverviewDto.AutomationRateBlock(automationPct, null, null),
                        false,
                        0,
                        0)));
  }

  private List<BusinessValueOverviewDto> capturedTargetWriteRows() {
    final ArgumentCaptor<List<BusinessValueOverviewDto>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(overviewWriter).bulkUpsertFromTargetWrite(captor.capture());
    return captor.getValue();
  }

  private List<BusinessValueOverviewDto> capturedRows() {
    final ArgumentCaptor<List<BusinessValueOverviewDto>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(overviewWriter).bulkUpsertFromScheduler(captor.capture());
    return captor.getValue();
  }

  private static List<MetricRange> allRanges() {
    return List.of(
        MetricRange.SEVEN_DAYS,
        MetricRange.THIRTY_DAYS,
        MetricRange.THREE_MONTHS,
        MetricRange.SIX_MONTHS);
  }

  private static SingleProcessReportDefinitionRequestDto seededReport() {
    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
            .definitions(Collections.emptyList())
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.DURATION))
            .groupBy(new ProcessDefinitionKeyGroupByDto())
            .build();
    reportData.setBusinessValueReport(true);
    final SingleProcessReportDefinitionRequestDto report =
        new SingleProcessReportDefinitionRequestDto();
    report.setId(BusinessValueDashboardService.DURATION_BY_PROCESS_REPORT_ID);
    report.setData(reportData);
    return report;
  }

  /**
   * Mirrors the group-by-process-definition-key shape: one entry per definition that has data, with
   * definitions lacking data simply absent from the result.
   */
  private AuthorizedReportEvaluationResult evaluationResultFor(
      final ProcessReportDataDto reportData) {
    final List<MapResultEntryDto> entries =
        reportData.getDefinitions().stream()
            .map(
                definition ->
                    new MapResultEntryDto(
                        bucketKeyOverrides.getOrDefault(definition.getKey(), definition.getKey()),
                        valueFor(definition)))
            .filter(entry -> entry.getValue() != null)
            .collect(Collectors.toList());

    final SingleProcessReportDefinitionRequestDto report =
        new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);

    final MapCommandResult commandResult =
        new MapCommandResult(List.of(MeasureDto.of(entries)), reportData);
    return new AuthorizedReportEvaluationResult(
        new SingleReportEvaluationResult<>(report, commandResult), RoleType.VIEWER);
  }

  /**
   * Resolves the value a definition should report. Reading the tenant off the pinned definition is
   * what lets a test prove a tenant's answer cannot land on another tenant's row — if the service
   * ever stopped pinning the tenant, this would return null and the assertions would fail.
   */
  private Double valueFor(final ReportDataDefinitionDto definition) {
    if (!valuesByTenant.isEmpty()) {
      return definition.getTenantIds().stream().findFirst().map(valuesByTenant::get).orElse(null);
    }
    return stubbedValuesByKey.get(definition.getKey());
  }

  private static SearchLimitsRepository searchLimitsWithBucketLimit(final int bucketLimit) {
    final SearchLimitsRepository searchLimits = mock(SearchLimitsRepository.class);
    when(searchLimits.aggregationBucketLimit()).thenReturn(bucketLimit);
    when(searchLimits.indexNamePrefix()).thenReturn("optimize");
    return searchLimits;
  }
}
