package org.camunda.optimize.service.es.filter.decision;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.service.es.filter.FilterOperatorConstants;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.junit.Test;

import java.time.OffsetDateTime;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createBooleanOutputVariableFilter;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createDoubleInputVariableFilter;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createFixedDateInputVariableFilter;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createRollingEvaluationDateFilter;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createStringInputVariableFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class DecisionMixedFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void resultWithAllFilterTypesApplied() {
    // given
    final OffsetDateTime dateTimeInputFilterStart = OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
    final double expectedAmountValue = 200.0;
    final String expectedCategory = "Misc";
    final String expectedAuditOutput = "false";

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition(
      "dmn/invoiceBusinessDecision_withDate.xml");
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(expectedAmountValue, "2019-06-06T00:00:00+00:00")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );

    final InputVariableFilterDto fixedDateInputVariableFilter = createFixedDateInputVariableFilter(
      INPUT_INVOICE_DATE_ID, dateTimeInputFilterStart, null
    );
    final InputVariableFilterDto doubleInputVariableFilter = createDoubleInputVariableFilter(
      INPUT_AMOUNT_ID,
      FilterOperatorConstants.IN,
      String.valueOf(expectedAmountValue)
    );
    final InputVariableFilterDto stringInputVariableFilter = createStringInputVariableFilter(
      INPUT_CATEGORY_ID, FilterOperatorConstants.IN, expectedCategory
    );
    final OutputVariableFilterDto booleanOutputVariableFilter = createBooleanOutputVariableFilter(
      OUTPUT_AUDIT_ID, expectedAuditOutput
    );
    final EvaluationDateFilterDto rollingEvaluationDateFilter = createRollingEvaluationDateFilter(1L, "days");

    reportData.setFilter(Lists.newArrayList(
      fixedDateInputVariableFilter,
      doubleInputVariableFilter,
      stringInputVariableFilter,
      booleanOutputVariableFilter,
      rollingEvaluationDateFilter
    ));
    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));

    assertThat(
      (String) result.getData().get(0).getInputVariables().get(INPUT_INVOICE_DATE_ID).getValue(),
      startsWith("2019-06-06T00:00:00")
    );
  }

}
