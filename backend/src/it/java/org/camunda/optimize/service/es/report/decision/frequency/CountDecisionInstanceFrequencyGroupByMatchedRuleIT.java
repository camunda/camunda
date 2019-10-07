/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.decision.frequency;

import com.google.common.collect.Lists;
import junitparams.JUnitParamsRunner;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResultDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createDoubleInputVariableFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public class CountDecisionInstanceFrequencyGroupByMatchedRuleIT extends AbstractDecisionDefinitionIT {

  private static final String INVOICE_RULE_1_ID = "DecisionRule_1of5a87";
  private static final String INVOICE_RULE_3_ID = "row-49839158-4";
  private static final String INVOICE_RULE_2_ID = "DecisionRule_1ak4z14";
  private static final String INVOICE_RULE_4_ID = "DecisionRule_0cuxolz";
  private static final String BEVERAGES_RULE_3_ID = "row-506282952-9";
  private static final String BEVERAGES_RULE_5_ID = "row-506282952-11";
  private static final String BEVERAGES_RULE_6_ID = "row-506282952-12";

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupByMatchedRule() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // rule 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    // rule 3
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc")
    );
    // rule 4
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineRule.deployAndStartDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, decisionDefinitionVersion1
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(6L));
    assertThat(result.getIsComplete(), is(true));

    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(4));
    assertThat(result.getEntryForKey(INVOICE_RULE_1_ID).get().getValue(), is(2L));
    assertThat(result.getEntryForKey(INVOICE_RULE_2_ID).get().getValue(), is(1L));
    assertThat(result.getEntryForKey(INVOICE_RULE_3_ID).get().getValue(), is(2L));
    assertThat(result.getEntryForKey(INVOICE_RULE_4_ID).get().getValue(), is(1L));
  }

  @Test
  public void reportEvaluationMultiBuckets_resultLimitedByConfig() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // rule 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    // rule 3
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc")
    );
    // rule 4
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineRule.deployAndStartDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, decisionDefinitionVersion1
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(6L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    assertThat(result.getIsComplete(), is(false));
  }

  @Test
  public void testCustomOrderOnMatchedRuleKeyIsApplied() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // rule 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    // rule 3
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc")
    );
    // rule 4
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));
    final DecisionReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(4));
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(
      resultKeys,
      // expect ascending order
      contains(resultKeys.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnMatchedRuleValueIsApplied() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // rule 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    // rule 3
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc")
    );
    // rule 4
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final DecisionReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(4));
    final List<Long> bucketValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(
      bucketValues,
      contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray()) 
    );
  }

  @Test
  public void reportEvaluationMultiBucketsAllVersionsGroupByMatchedRule() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // rule 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    // rule 3
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc")
    );
    // rule 4
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineRule.deployDecisionDefinition();

    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto2.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto2.getId(), createInputs(200.0, "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(8L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(4));
    assertThat(result.getEntryForKey(INVOICE_RULE_1_ID).get().getValue(), is(4L));
    assertThat(result.getEntryForKey(INVOICE_RULE_2_ID).get().getValue(), is(1L));
    assertThat(result.getEntryForKey(INVOICE_RULE_3_ID).get().getValue(), is(2L));
    assertThat(result.getEntryForKey(INVOICE_RULE_4_ID).get().getValue(), is(1L));
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupByMatchedRuleFilterByInput() {
    // given
    final double inputVariableValueToFilterFor = 300.0;
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(), createInputs(200.0, "Misc")
    );
    // rule 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(), createInputs(300.0, "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .setFilter(createDoubleInputVariableFilter(
        INPUT_AMOUNT_ID, FilterOperatorConstants.GREATER_THAN_EQUALS, String.valueOf(inputVariableValueToFilterFor)
      ))
      .build();
    final DecisionReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    assertThat(resultData.get(0).getKey(), is(INVOICE_RULE_2_ID));
    assertThat(resultData.get(0).getValue(), is(1L));
  }

  @Test
  public void reportEvaluationOnCollectPolicyMultiBucketsAllVersionsGroupByMatchedRule() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDishDecisionDefinition();
    // triggers rule 3 and 5
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createDishInputs("Winter", 8, true)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createDishInputs("Winter", 8, true)
    );

    // triggers rule 6
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createDishInputs("Winter", 8, false)
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(3L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(3));
    assertThat(result.getEntryForKey(BEVERAGES_RULE_3_ID).get().getValue(), is(2L));
    assertThat(result.getEntryForKey(BEVERAGES_RULE_5_ID).get().getValue(), is(2L));
    assertThat(result.getEntryForKey(BEVERAGES_RULE_6_ID).get().getValue(), is(1L));
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByMatchedRuleOtherDefinitionsHaveNoSideEffect() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    // other decision definition
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDishDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto2.getId(), createDishInputs("Winter", 8, true)
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(2L));
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    assertThat(resultData.get(0).getValue(), is(2L));
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Lists.newArrayList(tenantId1);
    final String decisionDefinitionKey = deployAndStartMultiTenantDefinition(
      Lists.newArrayList(null, tenantId1, tenantId2)
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionKey)
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setTenantIds(selectedTenants)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    DecisionReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is((long) selectedTenants.size()));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
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
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    reportData.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus(), is(400));
  }

  private DecisionDefinitionEngineDto deployDishDecisionDefinition() {
    return engineRule.deployDecisionDefinition("dmn/decide-dish.xml");
  }

  private HashMap<String, InputVariableEntry> createDishInputs(final String season,
                                                               final Integer guestCount,
                                                               final boolean withChildren) {
    return new HashMap<String, InputVariableEntry>() {{
      put(INPUT_SEASON_ID, new InputVariableEntry(INPUT_SEASON_ID, "Season", VariableType.STRING, season));
      put(
        INPUT_NUMBER_OF_GUESTS_ID,
        new InputVariableEntry(
          INPUT_NUMBER_OF_GUESTS_ID,
          "Number of Guests",
          VariableType.INTEGER,
          guestCount
        )
      );
      put(
        INPUT_GUEST_WITH_CHILDREN_ID,
        new InputVariableEntry(
          INPUT_GUEST_WITH_CHILDREN_ID,
          "Guests with children?",
          VariableType.BOOLEAN,
          withChildren
        )
      );
    }};
  }

  private AuthorizedDecisionReportEvaluationResultDto<DecisionReportMapResultDto> evaluateDecisionInstanceFrequencyByMatchedRule(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion) {
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    return evaluateMapReport(reportData);
  }

}
