/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.rest.AgenticPhase2Guard;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.identity.CollapsedSubprocessNodesService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.variable.ProcessVariableService;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportEvaluationHandlerTest {

  private static final String USER_ID = "user";

  @Mock private ReportService reportService;
  @Mock private SingleReportEvaluator singleReportEvaluator;
  @Mock private CombinedReportEvaluator combinedReportEvaluator;
  @Mock private ProcessVariableService processVariableService;
  @Mock private DefinitionService definitionService;
  @Mock private CollapsedSubprocessNodesService collapsedSubprocessNodesService;

  private ConfigurationService configurationService;
  private ReportEvaluationHandler underTest;

  @BeforeEach
  void setUp() {
    configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
    underTest =
        new PlainReportEvaluationHandler(
            reportService,
            singleReportEvaluator,
            combinedReportEvaluator,
            processVariableService,
            definitionService,
            collapsedSubprocessNodesService,
            new AgenticPhase2Guard(configurationService));
  }

  @Test
  void shouldFillInAllFullyImportedDefinitionsForAgentInsightsReport() {
    // given
    configurationService.getAgenticPhase2Configuration().setEnabled(true);
    final String processKey = uniqueKey();
    final ProcessReportDataDto reportData = agentRunReportData();
    reportData.setAgentInsightsReport(true);
    when(definitionService.getFullyImportedDefinitions(DefinitionType.PROCESS, USER_ID))
        .thenReturn(List.of(definition(processKey, "Process", "tenant-a")));

    // when
    underTest.evaluateReport(evaluationWithoutSelection(reportData));

    // then the stored report has no definitions, so the user's definitions are filled in
    assertThat(reportData.getDefinitions())
        .singleElement()
        .satisfies(
            definition -> {
              assertThat(definition.getKey()).isEqualTo(processKey);
              assertThat(definition.getTenantIds()).containsExactly("tenant-a");
            });
  }

  @Test
  void shouldHideAgentInsightsReportWhenPhase2IsOff() {
    // given
    final ProcessReportDataDto reportData = agentRunReportData();
    reportData.setAgentInsightsReport(true);

    // when / then
    assertThatThrownBy(() -> underTest.evaluateReport(evaluationWithoutSelection(reportData)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void shouldRejectAgentRunViewOutsideAgentInsightsReport() {
    // given
    configurationService.getAgenticPhase2Configuration().setEnabled(true);
    final ProcessReportDataDto reportData = agentRunReportData();

    // when / then
    assertThatThrownBy(() -> underTest.evaluateReport(evaluationWithoutSelection(reportData)))
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("agentRun");
  }

  @Test
  void shouldNameSelectedProcessFromAuthorizedTenantsOnly() {
    // given a process deployed in an authorized and an unauthorized tenant, where the
    // tenant-agnostic lookup picks the name from the unauthorized one
    final String processKey = uniqueKey();
    final ProcessReportDataDto reportData = businessValueReportData();
    when(definitionService.getDefinitionWithAvailableTenants(
            DefinitionType.PROCESS, processKey, USER_ID))
        .thenReturn(Optional.of(definition(processKey, "Unauthorized tenant name", "tenant-a")));
    when(definitionService.getFullyImportedDefinitions(
            DefinitionType.PROCESS, Set.of(processKey), List.of("tenant-a"), USER_ID))
        .thenReturn(List.of(definition(processKey, "Authorized tenant name", "tenant-a")));

    // when
    underTest.evaluateReport(evaluationSelecting(reportData, processKey));

    // then the injected definition only carries the name visible in the user's tenants
    assertThat(reportData.getDefinitions())
        .singleElement()
        .satisfies(
            definition -> {
              assertThat(definition.getKey()).isEqualTo(processKey);
              assertThat(definition.getTenantIds()).containsExactly("tenant-a");
              assertThat(definition.getName()).isEqualTo("Authorized tenant name");
              assertThat(definition.getDisplayName()).isEqualTo("Authorized tenant name");
            });
  }

  @Test
  void shouldClearNameWhenSelectedProcessHasNoNameInAuthorizedTenants() {
    // given
    final String processKey = uniqueKey();
    final ProcessReportDataDto reportData = businessValueReportData();
    when(definitionService.getDefinitionWithAvailableTenants(
            DefinitionType.PROCESS, processKey, USER_ID))
        .thenReturn(Optional.of(definition(processKey, "Unauthorized tenant name", "tenant-a")));
    when(definitionService.getFullyImportedDefinitions(
            DefinitionType.PROCESS, Set.of(processKey), List.of("tenant-a"), USER_ID))
        .thenReturn(List.of());

    // when
    underTest.evaluateReport(evaluationSelecting(reportData, processKey));

    // then no name leaks through, so labels fall back to the process id
    assertThat(reportData.getDefinitions())
        .singleElement()
        .satisfies(
            definition -> {
              assertThat(definition.getName()).isNull();
              assertThat(definition.getDisplayName()).isNull();
            });
  }

  @Test
  void shouldStillRejectSelectedProcessTheUserIsNotAuthorizedFor() {
    // given
    final String processKey = uniqueKey();
    final ProcessReportDataDto reportData = businessValueReportData();
    when(definitionService.getDefinitionWithAvailableTenants(
            DefinitionType.PROCESS, processKey, USER_ID))
        .thenThrow(new ForbiddenException("not authorized"));

    // when / then the report is rejected rather than evaluated without definition scoping
    assertThatThrownBy(() -> underTest.evaluateReport(evaluationSelecting(reportData, processKey)))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldLookUpNamesOnceForSelectedProcessesSharingTheSameTenants() {
    // given two selected processes available in the same tenant
    final String firstKey = uniqueKey();
    final String secondKey = uniqueKey();
    final ProcessReportDataDto reportData = businessValueReportData();
    when(definitionService.getDefinitionWithAvailableTenants(
            DefinitionType.PROCESS, firstKey, USER_ID))
        .thenReturn(Optional.of(definition(firstKey, "Unscoped first", "tenant-a")));
    when(definitionService.getDefinitionWithAvailableTenants(
            DefinitionType.PROCESS, secondKey, USER_ID))
        .thenReturn(Optional.of(definition(secondKey, "Unscoped second", "tenant-a")));
    when(definitionService.getFullyImportedDefinitions(
            DefinitionType.PROCESS, Set.of(firstKey, secondKey), List.of("tenant-a"), USER_ID))
        .thenReturn(
            List.of(
                definition(firstKey, "First process", "tenant-a"),
                definition(secondKey, "Second process", "tenant-a")));

    // when
    underTest.evaluateReport(evaluationSelecting(reportData, firstKey, secondKey));

    // then both names come from a single scoped lookup
    assertThat(reportData.getDefinitions())
        .extracting(ReportDataDefinitionDto::getKey, ReportDataDefinitionDto::getDisplayName)
        .containsExactly(tuple(firstKey, "First process"), tuple(secondKey, "Second process"));
    verify(definitionService, times(1))
        .getFullyImportedDefinitions(eq(DefinitionType.PROCESS), anySet(), anyList(), any());
  }

  @Test
  void shouldLookUpNamesPerTenantSetForSelectedProcessesInDifferentTenants() {
    // given two selected processes available in different tenants
    final String firstKey = uniqueKey();
    final String secondKey = uniqueKey();
    final ProcessReportDataDto reportData = businessValueReportData();
    when(definitionService.getDefinitionWithAvailableTenants(
            DefinitionType.PROCESS, firstKey, USER_ID))
        .thenReturn(Optional.of(definition(firstKey, "Unscoped first", "tenant-a")));
    when(definitionService.getDefinitionWithAvailableTenants(
            DefinitionType.PROCESS, secondKey, USER_ID))
        .thenReturn(Optional.of(definition(secondKey, "Unscoped second", "tenant-b")));
    when(definitionService.getFullyImportedDefinitions(
            DefinitionType.PROCESS, Set.of(firstKey), List.of("tenant-a"), USER_ID))
        .thenReturn(List.of(definition(firstKey, "First in tenant a", "tenant-a")));
    when(definitionService.getFullyImportedDefinitions(
            DefinitionType.PROCESS, Set.of(secondKey), List.of("tenant-b"), USER_ID))
        .thenReturn(List.of(definition(secondKey, "Second in tenant b", "tenant-b")));

    // when
    underTest.evaluateReport(evaluationSelecting(reportData, firstKey, secondKey));

    // then each name is resolved only within its own tenants
    assertThat(reportData.getDefinitions())
        .extracting(ReportDataDefinitionDto::getKey, ReportDataDefinitionDto::getDisplayName)
        .containsExactly(
            tuple(firstKey, "First in tenant a"), tuple(secondKey, "Second in tenant b"));
  }

  // Seeded system reports are stored with an empty definition list. The query treats an empty list
  // as "match nothing" (camunda/camunda#70131), so the dashboards only show data because the
  // handler replaces that placeholder with the user's authorized definitions before evaluation.
  @ParameterizedTest(name = "{0}")
  @MethodSource("seededSystemReports")
  void shouldInjectAuthorizedDefinitionsIntoSeededSystemReportWithEmptyDefinitionList(
      final String flavor, final ProcessReportDataDto reportData) {
    // given a seeded report with no stored definitions and processes in two authorized tenants
    final String firstKey = uniqueKey();
    final String secondKey = uniqueKey();
    reportData.setDefinitions(List.of());
    when(definitionService.getFullyImportedDefinitions(DefinitionType.PROCESS, USER_ID))
        .thenReturn(
            List.of(
                definition(firstKey, "First process", "tenant-a"),
                definition(secondKey, "Second process", "tenant-b")));

    // when evaluated without any process selected
    underTest.evaluateReport(evaluationWithoutSelection(reportData));

    // then the report evaluates over every authorized definition, scoped to its own tenants
    assertThat(reportData.getDefinitions())
        .extracting(ReportDataDefinitionDto::getKey, ReportDataDefinitionDto::getTenantIds)
        .containsExactly(
            tuple(firstKey, List.of("tenant-a")), tuple(secondKey, List.of("tenant-b")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("seededSystemReports")
  void shouldLeaveDefinitionsEmptyWhenUserHasNoAuthorizedDefinitions(
      final String flavor, final ProcessReportDataDto reportData) {
    // given a user whose tenants hold no imported process definitions
    reportData.setDefinitions(List.of());
    when(definitionService.getFullyImportedDefinitions(DefinitionType.PROCESS, USER_ID))
        .thenReturn(List.of());

    // when
    underTest.evaluateReport(evaluationWithoutSelection(reportData));

    // then no definitions are injected
    assertThat(reportData.getDefinitions()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("processScopedSystemReports")
  void shouldLeaveDefinitionsEmptyWhenSelectedProcessIsUnknown(
      final String flavor, final ProcessReportDataDto reportData) {
    // given a selected process key that matches no imported definition (a key that exists only in
    // tenants the user can't see is rejected with a ForbiddenException instead)
    final String unknownKey = uniqueKey();
    reportData.setDefinitions(List.of());
    when(definitionService.getDefinitionWithAvailableTenants(
            DefinitionType.PROCESS, unknownKey, USER_ID))
        .thenReturn(Optional.empty());

    // when
    underTest.evaluateReport(evaluationSelecting(reportData, unknownKey));

    // then no definitions are injected
    assertThat(reportData.getDefinitions()).isEmpty();
  }

  // Only business value and agentic reports honour a process selection (L1); management reports
  // always evaluate over every authorized definition.
  private static Stream<Arguments> processScopedSystemReports() {
    final ProcessReportDataDto agentic = new ProcessReportDataDto();
    agentic.setAgenticControlReport(true);
    return Stream.of(
        Arguments.of("business value report", businessValueReportData()),
        Arguments.of("agentic control report", agentic));
  }

  private static Stream<Arguments> seededSystemReports() {
    final ProcessReportDataDto agentic = new ProcessReportDataDto();
    agentic.setAgenticControlReport(true);
    final ProcessReportDataDto management = new ProcessReportDataDto();
    management.setManagementReport(true);
    return Stream.of(
        Arguments.of("business value report", businessValueReportData()),
        Arguments.of("agentic control report", agentic),
        Arguments.of("management report", management));
  }

  private static ProcessReportDataDto agentRunReportData() {
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(new ProcessViewDto(ProcessViewEntity.AGENT_RUN, ViewProperty.TOTAL_TOKENS));
    return reportData;
  }

  private static ReportEvaluationInfo evaluationWithoutSelection(
      final ProcessReportDataDto reportData) {
    return ReportEvaluationInfo.builder(new SingleProcessReportDefinitionRequestDto(reportData))
        .userId(USER_ID)
        .build();
  }

  private static ProcessReportDataDto businessValueReportData() {
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setBusinessValueReport(true);
    return reportData;
  }

  private static ReportEvaluationInfo evaluationSelecting(
      final ProcessReportDataDto reportData, final String... processKeys) {
    final AdditionalProcessReportEvaluationFilterDto filters =
        new AdditionalProcessReportEvaluationFilterDto(List.of());
    filters.setDefinitions(Arrays.stream(processKeys).map(ReportDataDefinitionDto::new).toList());
    return ReportEvaluationInfo.builder(new SingleProcessReportDefinitionRequestDto(reportData))
        .userId(USER_ID)
        .additionalFilters(filters)
        .build();
  }

  private static DefinitionResponseDto definition(
      final String key, final String name, final String tenantId) {
    return new DefinitionResponseDto(
        key,
        name,
        DefinitionType.PROCESS,
        List.of(new TenantDto(tenantId, tenantId, "zeebe")),
        "zeebe");
  }

  private static String uniqueKey() {
    return "process-" + UUID.randomUUID();
  }
}
