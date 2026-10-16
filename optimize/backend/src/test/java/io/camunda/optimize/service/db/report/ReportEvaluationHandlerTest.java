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
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.identity.CollapsedSubprocessNodesService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.variable.ProcessVariableService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

  private ReportEvaluationHandler underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new PlainReportEvaluationHandler(
            reportService,
            singleReportEvaluator,
            combinedReportEvaluator,
            processVariableService,
            definitionService,
            collapsedSubprocessNodesService);
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

  private static ProcessReportDataDto businessValueReportData() {
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setBusinessValueReport(true);
    return reportData;
  }

  private static ReportEvaluationInfo evaluationSelecting(
      final ProcessReportDataDto reportData, final String processKey) {
    final AdditionalProcessReportEvaluationFilterDto filters =
        new AdditionalProcessReportEvaluationFilterDto(List.of());
    filters.setDefinitions(List.of(new ReportDataDefinitionDto(processKey)));
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
