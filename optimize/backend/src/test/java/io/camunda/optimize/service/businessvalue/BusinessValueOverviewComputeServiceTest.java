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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
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
import io.camunda.optimize.service.db.repository.BusinessValueTargetRepository;
import io.camunda.optimize.service.db.repository.MappingMetadataRepository;
import io.camunda.optimize.service.db.writer.BusinessValueOverviewWriter;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ElasticSearchConfiguration;
import io.camunda.optimize.service.util.configuration.OpenSearchConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Guards the property the fan-in exists for: report evaluations per sweep scale with the number of
 * definition <em>chunks</em> rather than with the definition count, so 250 extra definitions cost
 * one extra evaluation per report and range instead of 250.
 */
class BusinessValueOverviewComputeServiceTest {

  private static final String DEFAULT_TENANT = "<default>";
  private static final int CHUNK_SIZE = 250;

  private BusinessValueTargetRepository targetRepository;
  private BusinessValueOverviewWriter overviewWriter;
  private DefinitionService definitionService;
  private ReportService reportService;
  private PlainReportEvaluationHandler reportEvaluationHandler;
  private MappingMetadataRepository mappingMetadataRepository;
  private BusinessValueOverviewComputeService computeService;

  /** Values the stubbed evaluation should return, keyed by process definition key. */
  private final Map<String, Double> stubbedValuesByKey = new java.util.HashMap<>();

  /**
   * Values keyed by tenant instead, for asserting that a tenant's answer lands only on that
   * tenant's rows. Takes precedence over {@link #stubbedValuesByKey} when populated.
   */
  private final Map<String, Double> valuesByTenant = new java.util.HashMap<>();

  private final AtomicInteger evaluationCount = new AtomicInteger();

  @BeforeEach
  void setUp() {
    targetRepository = mock(BusinessValueTargetRepository.class);
    overviewWriter = mock(BusinessValueOverviewWriter.class);
    definitionService = mock(DefinitionService.class);
    reportService = mock(ReportService.class);
    reportEvaluationHandler = mock(PlainReportEvaluationHandler.class);
    mappingMetadataRepository = mock(MappingMetadataRepository.class);

    when(targetRepository.scanAll()).thenReturn(List.of());
    when(reportService.getReportDefinition(anyString())).thenAnswer(invocation -> seededReport());

    when(reportEvaluationHandler.evaluateReport(any(ReportEvaluationInfo.class)))
        .thenAnswer(
            invocation -> {
              evaluationCount.incrementAndGet();
              final ReportEvaluationInfo info = invocation.getArgument(0);
              final ProcessReportDataDto reportData =
                  (ProcessReportDataDto) info.getReport().getData();
              return evaluationResultFor(reportData);
            });

    computeService =
        new BusinessValueOverviewComputeService(
            targetRepository,
            overviewWriter,
            definitionService,
            reportService,
            reportEvaluationHandler,
            mappingMetadataRepository,
            configurationServiceWithBucketLimit(1000));
  }

  @Test
  void shouldEvaluateOncePerReportRangeAndChunkRatherThanPerDefinition() {
    // given a single tenant holding exactly one chunk worth of definitions
    givenDefinitions(DEFAULT_TENANT, CHUNK_SIZE);

    // when computing across all four range presets
    computeService.computeOverviewRows(allRanges());

    // then two reports x four ranges x one chunk
    assertThat(evaluationCount.get()).isEqualTo(2 * 4 * 1);
  }

  @Test
  void shouldScaleEvaluationsWithChunkCountNotDefinitionCount() {
    // given a catalog of 500 definitions on one tenant
    givenDefinitions(DEFAULT_TENANT, 500);
    computeService.computeOverviewRows(allRanges());
    final int evaluationsForFiveHundred = evaluationCount.getAndSet(0);

    // when the catalog doubles
    givenDefinitions(DEFAULT_TENANT, 1000);
    computeService.computeOverviewRows(allRanges());
    final int evaluationsForOneThousand = evaluationCount.get();

    // then doubling the catalog doubles the chunk count and so doubles the evaluations, but the
    // unit of growth is the 250-definition chunk: 1000 definitions cost 32 evaluations, against the
    // 8000 a per-definition sweep would issue
    assertThat(evaluationsForFiveHundred).isEqualTo(2 * 4 * 2);
    assertThat(evaluationsForOneThousand).isEqualTo(2 * 4 * 4);
  }

  @Test
  void shouldChunkAtTheBucketLimitBoundary() {
    // given one more definition than fits in a single chunk
    givenDefinitions(DEFAULT_TENANT, CHUNK_SIZE + 1);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the overflow definition forces a second chunk for both reports
    assertThat(evaluationCount.get()).isEqualTo(2 * 2);
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
            new ArrayList<>(java.util.Arrays.asList(null, DEFAULT_TENANT)),
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
    stubbedValuesByKey.put("Process_1", 7_000.0);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then it is still evaluated and its row carries the measured value
    assertThat(evaluationCount.get()).isEqualTo(2);
    assertThat(capturedRows())
        .singleElement()
        .satisfies(row -> assertThat(row.getCycleTime().getValue()).isEqualTo(7_000L));
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
            configurationServiceWithBucketLimit(10));
    givenDefinitions(DEFAULT_TENANT, 20);

    // when computing a single range
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the chunk follows the lowered limit rather than the default — a chunk larger than the
    // bucket limit would silently drop the definitions past it
    assertThat(evaluationCount.get()).isEqualTo(2 * 2);
  }

  private void givenDefinitions(final String tenantId, final int count) {
    givenDefinitionsAcrossTenants(Map.of(tenantId, count));
  }

  private void givenDefinitionsAcrossTenants(final Map<String, Integer> countsByTenant) {
    final List<DefinitionWithTenantIdsDto> definitions = new ArrayList<>();
    final Set<String> keys = new java.util.HashSet<>();
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
            .map(definition -> new MapResultEntryDto(definition.getKey(), valueFor(definition)))
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

  private static ConfigurationService configurationServiceWithBucketLimit(final int bucketLimit) {
    final ConfigurationService configurationService = mock(ConfigurationService.class);
    final ElasticSearchConfiguration elasticSearchConfiguration =
        mock(ElasticSearchConfiguration.class);
    final OpenSearchConfiguration openSearchConfiguration = mock(OpenSearchConfiguration.class);
    when(elasticSearchConfiguration.getAggregationBucketLimit()).thenReturn(bucketLimit);
    when(openSearchConfiguration.getAggregationBucketLimit()).thenReturn(bucketLimit);
    when(configurationService.getElasticSearchConfiguration())
        .thenReturn(elasticSearchConfiguration);
    when(configurationService.getOpenSearchConfiguration()).thenReturn(openSearchConfiguration);
    return configurationService;
  }
}
