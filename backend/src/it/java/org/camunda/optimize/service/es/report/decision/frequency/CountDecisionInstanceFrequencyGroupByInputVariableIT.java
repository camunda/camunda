package org.camunda.optimize.service.es.report.decision.frequency;

import com.google.common.collect.Lists;
import junitparams.JUnitParamsRunner;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportMapResultDto;
import org.camunda.optimize.service.es.filter.FilterOperatorConstants;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.DecisionReportDataType;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Iterator;

import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createDoubleInputVariableFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public class CountDecisionInstanceFrequencyGroupByInputVariableIT extends AbstractDecisionDefinitionIT {

  @Test
  public void reportEvaluationSingleBucketSpecificVersionGroupByNumberInputVariable() {
    // given
    final String amountValue = "100.0";
    final Double amountValueAsDouble = Double.valueOf(amountValue);
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineRule.deployAndStartDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1, decisionDefinitionVersion1, INPUT_AMOUNT_ID
    );

    // then
    assertThat(result.getDecisionInstanceCount(), is(2L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    assertThat(result.getResult().keySet().stream().findFirst().get(), is(amountValue));
    assertThat(result.getResult().values().stream().findFirst().get(), is(2L));
  }

  @Test
  public void reportEvaluationMultiBucketSpecificVersionGroupByInvoiceDateInputVariable() {
    // given
    final String dateGroupKey = "2018-01-01T00:00:00.000+0100";
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule
      .deployDecisionDefinition("dmn/invoiceBusinessDecision_withDate.xml");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(),
      createInputsWithDate(100.0, dateGroupKey)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(),
      createInputsWithDate(100.0, dateGroupKey)
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineRule.deployAndStartDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1, decisionDefinitionVersion1, INPUT_INVOICE_DATE_ID
    );

    // then
    assertThat(result.getDecisionInstanceCount(), is(2L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    assertThat(result.getResult().keySet().stream().findFirst().get(), is(dateGroupKey));
    assertThat(result.getResult().values().stream().findFirst().get(), is(2L));
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupByNumberInputVariable() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineRule.deployAndStartDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1, decisionDefinitionVersion1, INPUT_AMOUNT_ID
    );

    // then
    assertThat(result.getDecisionInstanceCount(), is(6L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(3));
    assertThat(
      new ArrayList<>(result.getResult().keySet()),
      contains("100.0", "200.0", "300.0")
    );
    final Iterator<Long> resultValuesIterator = result.getResult().values().iterator();
    assertThat(resultValuesIterator.next(), is(3L));
    assertThat(resultValuesIterator.next(), is(2L));
    assertThat(resultValuesIterator.next(), is(1L));
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupByNumberInputVariableFilterByValue() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineRule.deployAndStartDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .setVariableId(INPUT_AMOUNT_ID)
      .setFilter(Lists.newArrayList(createDoubleInputVariableFilter(
        INPUT_AMOUNT_ID, FilterOperatorConstants.IN, "200.0"
      )))
      .build();

    final DecisionReportMapResultDto result =  evaluateMapReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(2L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    assertThat(
      new ArrayList<>(result.getResult().keySet()),
      contains("200.0")
    );
    final Iterator<Long> resultValuesIterator = result.getResult().values().iterator();
    assertThat(resultValuesIterator.next(), is(2L));
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByNumberInputVariable() {
    // given
    final String amountValue = "100.0";
    final Double amountValueAsDouble = Double.valueOf(amountValue);
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );

    // different version
    engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, INPUT_AMOUNT_ID
    );

    // then
    assertThat(result.getDecisionInstanceCount(), is(4L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    assertThat(result.getResult().keySet().stream().findFirst().get(), is(amountValue));
    assertThat(result.getResult().values().stream().findFirst().get(), is(4L));
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByStringInputVariable() {
    // given
    final String categoryValue = "Misc";
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, categoryValue)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, categoryValue)
    );

    // different version
    engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, categoryValue)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, categoryValue)
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, INPUT_CATEGORY_ID
    );

    // then
    assertThat(result.getDecisionInstanceCount(), is(4L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    assertThat(result.getResult().keySet().stream().findFirst().get(), is(categoryValue));
    assertThat(result.getResult().values().stream().findFirst().get(), is(4L));
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByNumberInputVariableOtherDefinitionsHaveNoSideEffect() {
    // given
    final String amountValue = "100.0";
    final Double amountValueAsDouble = Double.valueOf(amountValue);
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );

    // same decision definition with same inputs but different key
    final DecisionDefinitionEngineDto otherDecisionDefinition = deployDefaultDecisionDefinitionWithDifferentKey(
      "otherKey");
    startDecisionInstanceWithInputVars(
      otherDecisionDefinition.getId(), createInputs(amountValueAsDouble, "Misc")
    );
    startDecisionInstanceWithInputVars(
      otherDecisionDefinition.getId(), createInputs(amountValueAsDouble, "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, INPUT_AMOUNT_ID
    );

    // then
    assertThat(result.getDecisionInstanceCount(), is(2L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    assertThat(result.getResult().keySet().stream().findFirst().get(), is(amountValue));
    assertThat(result.getResult().values().stream().findFirst().get(), is(2L));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
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
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .build();
    reportData.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus(), is(400));
  }

  private DecisionDefinitionEngineDto deployDefaultDecisionDefinitionWithDifferentKey(final String key) {
    final DmnModelInstance dmnModelInstance = Dmn.readModelFromStream(
      getClass().getClassLoader().getResourceAsStream(EngineIntegrationRule.DEFAULT_DMN_DEFINITION_PATH)
    );
    dmnModelInstance.getDefinitions().getDrgElements().stream()
      .findFirst()
      .ifPresent(drgElement -> drgElement.setId(key));
    return engineRule.deployDecisionDefinition(dmnModelInstance);
  }

  private DecisionReportMapResultDto evaluateDecisionInstanceFrequencyByInputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId) {
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .setVariableId(variableId)
      .build();
    return evaluateMapReport(reportData);
  }

}
