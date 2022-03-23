/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.decision.variable;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createBooleanOutputVariableFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createFixedDateInputVariableFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createNumericInputVariableFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRollingEvaluationDateFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createStringInputVariableFilter;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_CATEGORY_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_INVOICE_DATE_ID;
import static org.camunda.optimize.util.DmnModels.OUTPUT_AUDIT_ID;
import static org.camunda.optimize.util.DmnModels.createDecisionDefinitionWithDate;

public class DecisionMixedFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void createAndEvaluateReportWithAllFilterTypes() {
    // given
    final OffsetDateTime dateTimeInputFilterStart = OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
    final double expectedAmountValue = 200.0;
    final String expectedCategory = "Misc";

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition(
      createDecisionDefinitionWithDate()
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(expectedAmountValue, "2019-06-06T00:00:00+00:00")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();

    final InputVariableFilterDto fixedDateInputVariableFilter = createFixedDateInputVariableFilter(
      INPUT_INVOICE_DATE_ID, dateTimeInputFilterStart, null
    );
    final InputVariableFilterDto doubleInputVariableFilter = createNumericInputVariableFilter(
      INPUT_AMOUNT_ID,
      FilterOperator.IN,
      String.valueOf(expectedAmountValue)
    );

    final InputVariableFilterDto stringInputVariableFilter = createStringInputVariableFilter(
      INPUT_CATEGORY_ID, FilterOperator.IN, expectedCategory
    );
    final OutputVariableFilterDto booleanOutputVariableFilter = createBooleanOutputVariableFilter(
      OUTPUT_AUDIT_ID, Collections.singletonList(false)
    );
    final EvaluationDateFilterDto rollingEvaluationDateFilter = createRollingEvaluationDateFilter(
      1L, DateUnit.DAYS
    );

    reportData.setFilter(Lists.newArrayList(
      fixedDateInputVariableFilter,
      doubleInputVariableFilter,
      stringInputVariableFilter,
      booleanOutputVariableFilter,
      rollingEvaluationDateFilter
    ));
    final String reportId = reportClient.createSingleDecisionReport(reportData);
    final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = reportClient
      .evaluateDecisionRawReportById(reportId).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).hasSize(1);
    assertThat((String) result.getData().get(0).getInputVariables().get(INPUT_INVOICE_DATE_ID).getValue())
      .startsWith("2019-06-06T00:00:00");
  }

}
