/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.CombinedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResponseDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.db.report.result.NumberCommandResult;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ReportRestMapperTest {

  private static final String LOCALE = "en";

  private final AbstractIdentityService identityService = mock(AbstractIdentityService.class);
  private final LocalizationService localizationService = mock(LocalizationService.class);

  private final ReportRestMapper underTest =
      new ReportRestMapper(identityService, localizationService);

  @Test
  void shouldLocalizeAgenticControlCombinedReportNameViaAgenticControlCategory() {
    // given a combined report made of agentic-control sub-reports whose name is a localization code
    when(localizationService.validateAndReturnValidLocale(LOCALE)).thenReturn(LOCALE);
    when(localizationService.getLocalizationForAgenticControlReportCode(
            LOCALE, "agenticKpiTokenTrendName"))
        .thenReturn("Token Trend");

    // when
    final var response =
        underTest.mapToLocalizedEvaluationResponseDto(
            agenticCombinedReportEvaluation("agenticKpiTokenTrendName"), LOCALE);

    // then the combined report name is replaced with its localized value
    assertThat(
            ((AuthorizedCombinedReportEvaluationResponseDto<?>) response)
                .getReportDefinition()
                .getName())
        .isEqualTo("Token Trend");
  }

  private AuthorizedReportEvaluationResult agenticCombinedReportEvaluation(
      final String combinedReportName) {
    final ProcessReportDataDto subReportData =
        ProcessReportDataDto.builder()
            .view(new ProcessViewDto(ProcessViewEntity.PROCESS_INSTANCE, ViewProperty.FREQUENCY))
            .agenticControlReport(true)
            .build();
    final SingleProcessReportDefinitionRequestDto subReportDefinition =
        new SingleProcessReportDefinitionRequestDto(subReportData);
    subReportDefinition.setId("sub-report-id");

    final SingleReportEvaluationResult<Double> singleResult =
        new SingleReportEvaluationResult<>(
            subReportDefinition, new NumberCommandResult(subReportData));

    final CombinedReportDefinitionRequestDto combinedDefinition =
        new CombinedReportDefinitionRequestDto(new CombinedReportDataDto());
    combinedDefinition.setId("combined-report-id");
    combinedDefinition.setName(combinedReportName);

    return new AuthorizedReportEvaluationResult(
        new CombinedReportEvaluationResult(List.of(singleResult), 1L, combinedDefinition),
        RoleType.VIEWER);
  }
}
