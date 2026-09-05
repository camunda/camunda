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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
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
import io.camunda.optimize.service.db.report.PlainReportEvaluationHandler;
import io.camunda.optimize.service.db.report.ReportEvaluationInfo;
import io.camunda.optimize.service.db.report.result.MapCommandResult;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.db.repository.BusinessValueTargetRepository;
import io.camunda.optimize.service.db.repository.MappingMetadataRepository;
import io.camunda.optimize.service.db.repository.SearchLimitsRepository;
import io.camunda.optimize.service.db.writer.BusinessValueOverviewWriter;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.util.configuration.BusinessValueConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Covers the operational kill switch for the overview sweep. The value of a kill switch is entirely
 * in whether it actually stops work, so these assert that no report is evaluated and nothing is
 * written — not merely that the method returned.
 */
class BusinessValueOverviewComputeKillSwitchTest {

  private static final String TENANT = "<default>";

  private BusinessValueOverviewWriter overviewWriter;
  private PlainReportEvaluationHandler reportEvaluationHandler;
  private BusinessValueTargetRepository targetRepository;
  private BusinessValueOverviewRepository overviewRepository;
  private DefinitionService definitionService;
  private MappingMetadataRepository mappingMetadataRepository;
  private SearchLimitsRepository searchLimitsRepository;
  private BusinessValueConfiguration businessValueConfiguration;
  private BusinessValueOverviewComputeService computeService;

  @BeforeEach
  void setUp() {
    targetRepository = mock(BusinessValueTargetRepository.class);
    overviewRepository = mock(BusinessValueOverviewRepository.class);
    overviewWriter = mock(BusinessValueOverviewWriter.class);
    definitionService = mock(DefinitionService.class);
    final ReportService reportService = mock(ReportService.class);
    reportEvaluationHandler = mock(PlainReportEvaluationHandler.class);
    final ConfigurationService configurationService = mock(ConfigurationService.class);
    mappingMetadataRepository = mock(MappingMetadataRepository.class);
    searchLimitsRepository = mock(SearchLimitsRepository.class);
    businessValueConfiguration = new BusinessValueConfiguration();

    when(configurationService.getBusinessValueConfiguration())
        .thenReturn(businessValueConfiguration);
    when(targetRepository.scanAll()).thenReturn(List.of());
    when(mappingMetadataRepository.getProcessDefinitionKeysWithInstanceIndex())
        .thenReturn(java.util.Set.of("invoice-process"));
    when(searchLimitsRepository.aggregationBucketLimit()).thenReturn(1000);
    when(searchLimitsRepository.indexNamePrefix()).thenReturn("optimize");
    when(reportService.getReportDefinition(anyString())).thenAnswer(invocation -> seededReport());
    when(reportEvaluationHandler.evaluateReport(any(ReportEvaluationInfo.class)))
        .thenAnswer(invocation -> emptyEvaluationResult());
    when(definitionService.getAllDefinitionsWithTenants(DefinitionType.PROCESS))
        .thenReturn(
            List.of(
                new DefinitionWithTenantIdsDto(
                    "invoice-process",
                    "Invoice",
                    DefinitionType.PROCESS,
                    // getTenantIds() sorts in place, so this list has to be mutable
                    new ArrayList<>(List.of(TENANT)),
                    Collections.emptySet())));

    computeService =
        new BusinessValueOverviewComputeService(
            targetRepository,
            overviewRepository,
            overviewWriter,
            definitionService,
            reportService,
            reportEvaluationHandler,
            mappingMetadataRepository,
            searchLimitsRepository,
            configurationService);
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
    report.setData(reportData);
    return report;
  }

  private static AuthorizedReportEvaluationResult emptyEvaluationResult() {
    final SingleProcessReportDefinitionRequestDto report = seededReport();
    final MapCommandResult commandResult =
        new MapCommandResult(
            List.of(MeasureDto.of(List.<MapResultEntryDto>of())), report.getData());
    return new AuthorizedReportEvaluationResult(
        new SingleReportEvaluationResult<>(report, commandResult), RoleType.VIEWER);
  }

  @Test
  void shouldNotEvaluateOrWriteAnythingWhenComputeIsDisabled() {
    // given the sweep is switched off
    businessValueConfiguration.setOverviewComputeEnabled(false);

    // when a tick fires
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then nothing the sweep would have done happens. Asserting only on evaluation and the write
    // would leave the rest of the sweep untested — the definition scan, the target scan and the
    // instance-index lookup are all database round trips this switch exists to stop, and each one
    // sits before a chunk is ever built.
    verifyNoInteractions(
        reportEvaluationHandler,
        overviewWriter,
        definitionService,
        targetRepository,
        mappingMetadataRepository,
        searchLimitsRepository);
  }

  /**
   * The switch sheds background measurement, so a target write must stop evaluating too — otherwise
   * one save issues eight aggregations while the operator believes BVD is quiet. Falling back to
   * the stored values keeps the target itself coherent at no query cost, which is what the switch
   * is for: the numbers freeze, the dashboard keeps working.
   */
  @Test
  void shouldApplyATargetFromStoredValuesWithoutMeasuringWhenComputeIsDisabled() {
    // given the sweep is switched off, and a definition with rows already measured at 5s
    businessValueConfiguration.setOverviewComputeEnabled(false);
    when(overviewRepository.getByKey(eq(TENANT), eq("invoice-process"), any()))
        .thenAnswer(invocation -> Optional.of(rowFor(invocation.getArgument(2))));

    // when a 1s target is saved
    computeService.computeRowsForTarget(
        new BusinessValueTargetDto(
            "invoice-process",
            TENANT,
            1_000L,
            TargetValueUnit.MILLIS,
            null,
            OffsetDateTime.now(),
            "someone"));

    // then the verdict is brought up to date from the values already on the row, and nothing is
    // measured — asserting on the evaluation handler rather than only on the write, because a path
    // that evaluated and then wrote would satisfy the weaker assertion
    verifyNoInteractions(reportEvaluationHandler);
    final ArgumentCaptor<List<BusinessValueOverviewDto>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(overviewWriter).bulkUpsertFromTargetWrite(captor.capture());
    assertThat(captor.getValue())
        .isNotEmpty()
        .allSatisfy(
            row -> {
              assertThat(row.getCycleTime().getValue()).isEqualTo(5_000L);
              assertThat(row.getCycleTime().getTarget()).isEqualTo(1_000L);
              assertThat(row.getCycleTime().getMet()).isFalse();
            });
  }

  /**
   * With no row to fall back on there is nothing to apply the target to. The read path surfaces
   * this case instead, by joining live targets onto the rows it reads.
   */
  @Test
  void shouldWriteNothingWhenComputeIsDisabledAndTheDefinitionHasNoRows() {
    // given the sweep is switched off and the definition has never been measured
    businessValueConfiguration.setOverviewComputeEnabled(false);
    when(overviewRepository.getByKey(anyString(), anyString(), any())).thenReturn(Optional.empty());

    // when a target is saved
    computeService.computeRowsForTarget(
        new BusinessValueTargetDto(
            "invoice-process",
            TENANT,
            1_000L,
            TargetValueUnit.MILLIS,
            null,
            OffsetDateTime.now(),
            "someone"));

    // then nothing is measured and nothing is written
    verifyNoInteractions(reportEvaluationHandler);
    verify(overviewWriter, never()).bulkUpsertFromTargetWrite(any());
  }

  private static BusinessValueOverviewDto rowFor(final MetricRange range) {
    return new BusinessValueOverviewDto(
        TENANT,
        "invoice-process",
        "Invoice",
        range,
        OffsetDateTime.now(),
        new BusinessValueOverviewDto.CycleTimeBlock(5_000L, null, null),
        new BusinessValueOverviewDto.AutomationRateBlock(50d, null, null),
        false,
        0,
        0);
  }

  @Test
  void shouldSweepWhenComputeIsEnabled() {
    // given the sweep is switched on
    businessValueConfiguration.setOverviewComputeEnabled(true);

    // when a tick fires
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the sweep runs and writes, so the disabled case above is a real difference rather than
    // a no-op the fixture would have produced anyway
    verify(overviewWriter).bulkUpsertFromScheduler(any());
  }

  @Test
  void shouldSweepWhenTheFlagIsAbsentFromConfiguration() {
    // given no explicit setting, as on any deployment that has not opted in
    businessValueConfiguration.setOverviewComputeEnabled(null);

    // when a tick fires
    computeService.computeOverviewRows(List.of(MetricRange.SEVEN_DAYS));

    // then the sweep still runs: a kill switch that failed closed would silently disable the
    // feature on a configuration mistake, which is the opposite of what it is for
    verify(overviewWriter).bulkUpsertFromScheduler(any());
  }
}
