package org.camunda.optimize.service.es.report.decision.frequency;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportNumberResultDto;
import org.camunda.optimize.service.es.filter.FilterOperatorConstants;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.DecisionReportDataType;
import org.junit.Test;

import javax.ws.rs.core.Response;

import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createDoubleInputVariableFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class CountDecisionInstanceFrequencyGroupByNoneIT extends AbstractDecisionDefinitionIT {

  @Test
  public void reportEvaluationMultiInstancesSpecificVersion() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployAndStartSimpleDecisionDefinition("key");
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final DecisionReportNumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(3L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData(), is(3L));
  }

  @Test
  public void reportEvaluationMultiInstancesAllVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployAndStartSimpleDecisionDefinition("key");
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final DecisionReportNumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData(), is(5L));
  }

  @Test
  public void reportEvaluationMultiInstancesAllVersionsOtherDefinitionsHaveNoSideEffect() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployAndStartSimpleDecisionDefinition("key");
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    // other decision definition
    deployAndStartSimpleDecisionDefinition("key2");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final DecisionReportNumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData(), is(5L));
  }

  @Test
  public void reportEvaluationMultiInstancesFilter() {
    // given
    final double inputVariableValueToFilterFor = 200.0;
    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(inputVariableValueToFilterFor, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(inputVariableValueToFilterFor + 100.0, "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(String.valueOf(decisionDefinitionDto.getVersion()))
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .setFilter(createDoubleInputVariableFilter(
        INPUT_AMOUNT_ID, FilterOperatorConstants.GREATER_THAN_EQUALS, String.valueOf(inputVariableValueToFilterFor)
      ))
      .build();
   final DecisionReportNumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(2L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData(), is(2L));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    reportData.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    reportData.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus(), is(400));
  }

}
