/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.decision.frequency;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResultDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createNumericInputVariableFilter;
import static org.camunda.optimize.util.DmnModels.BEVERAGES_RULE_1_ID;
import static org.camunda.optimize.util.DmnModels.BEVERAGES_RULE_2_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_GUEST_WITH_CHILDREN_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_NUMBER_OF_GUESTS_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_SEASON_ID;
import static org.camunda.optimize.util.DmnModels.INVOICE_RULE_1_ID;
import static org.camunda.optimize.util.DmnModels.INVOICE_RULE_2_ID;
import static org.camunda.optimize.util.DmnModels.INVOICE_RULE_3_ID;
import static org.camunda.optimize.util.DmnModels.INVOICE_RULE_4_ID;
import static org.camunda.optimize.util.DmnModels.createDecideDishDecisionDefinition;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

public class CountDecisionInstanceFrequencyGroupByMatchedRuleIT extends AbstractDecisionDefinitionIT {

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupByMatchedRule() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
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
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, decisionDefinitionVersion1
    ).getResult();

    // then
    assertThat(result.getInstanceCount(), is(6L));
    assertThat(result.getIsComplete(), is(true));

    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(4));
    assertThat(result.getEntryForKey(INVOICE_RULE_1_ID).get().getValue(), is(2.));
    assertThat(result.getEntryForKey(INVOICE_RULE_2_ID).get().getValue(), is(1.));
    assertThat(result.getEntryForKey(INVOICE_RULE_3_ID).get().getValue(), is(2.));
    assertThat(result.getEntryForKey(INVOICE_RULE_4_ID).get().getValue(), is(1.));
  }

  @Test
  public void testCustomOrderOnMatchedRuleKeyIsApplied() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
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

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
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
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
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

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(4));
    final List<Double> bucketValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(
      bucketValues,
      contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void reportEvaluationMultiBucketsAllVersionsGroupByMatchedRule() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
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
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployDecisionDefinition();

    // rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto2.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto2.getId(), createInputs(200.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS
    ).getResult();

    // then
    assertThat(result.getInstanceCount(), is(8L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(4));
    assertThat(result.getEntryForKey(INVOICE_RULE_1_ID).get().getValue(), is(4.));
    assertThat(result.getEntryForKey(INVOICE_RULE_2_ID).get().getValue(), is(1.));
    assertThat(result.getEntryForKey(INVOICE_RULE_3_ID).get().getValue(), is(2.));
    assertThat(result.getEntryForKey(INVOICE_RULE_4_ID).get().getValue(), is(1.));
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupByMatchedRuleFilterByInput() {
    // given
    final double inputVariableValueToFilterFor = 300.0;
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
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

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .setFilter(createNumericInputVariableFilter(
        INPUT_AMOUNT_ID, FilterOperator.GREATER_THAN_EQUALS, String.valueOf(inputVariableValueToFilterFor)
      ))
      .build();
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    assertThat(resultData.get(0).getKey(), is(INVOICE_RULE_2_ID));
    assertThat(resultData.get(0).getValue(), is(1.));
  }

  @Test
  public void reportEvaluationOnCollectPolicyMultiBucketsAllVersionsGroupByMatchedRule() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDishDecisionDefinition();
    // triggers rule 1
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createDishInputs("Winter", 8, true)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createDishInputs("Winter", 8, true)
    );

    // triggers rule 2
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createDishInputs("Winter", 8, false)
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS
    ).getResult();

    // then
    assertThat(result.getInstanceCount(), is(3L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    assertThat(result.getEntryForKey(BEVERAGES_RULE_1_ID).get().getValue(), is(2.));
    assertThat(result.getEntryForKey(BEVERAGES_RULE_2_ID).get().getValue(), is(1.));
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByMatchedRuleOtherDefinitionsHaveNoSideEffect() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
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

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS
    ).getResult();

    // then
    assertThat(result.getInstanceCount(), is(2L));
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    assertThat(resultData.get(0).getValue(), is(2.));
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

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionKey)
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setTenantIds(selectedTenants)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is((long) selectedTenants.size()));
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

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
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

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  private DecisionDefinitionEngineDto deployDishDecisionDefinition() {
    return engineIntegrationExtension.deployDecisionDefinition(createDecideDishDecisionDefinition());
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

  private AuthorizedDecisionReportEvaluationResultDto<ReportMapResultDto> evaluateDecisionInstanceFrequencyByMatchedRule(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion) {
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    return reportClient.evaluateMapReport(reportData);
  }

}
