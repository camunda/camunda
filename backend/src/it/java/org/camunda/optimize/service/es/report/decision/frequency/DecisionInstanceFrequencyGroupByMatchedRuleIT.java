/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.decision.frequency;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
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

public class DecisionInstanceFrequencyGroupByMatchedRuleIT extends AbstractDecisionDefinitionIT {

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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, decisionDefinitionVersion1
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(6L);

    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(4);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_1_ID).get().getValue())
      .isEqualTo(2.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_2_ID).get().getValue())
      .isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_3_ID).get().getValue())
      .isEqualTo(2.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_4_ID).get().getValue())
      .isEqualTo(1.);
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(4);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.reverseOrder());
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(4);
    final List<Double> bucketValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(bucketValues).isSortedAccordingTo(Comparator.naturalOrder());
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(8L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(4);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_1_ID).get().getValue())
      .isEqualTo(4.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_2_ID).get().getValue())
      .isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_3_ID).get().getValue())
      .isEqualTo(2.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_4_ID).get().getValue())
      .isEqualTo(1.);
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo(INVOICE_RULE_2_ID);
    assertThat(resultData.get(0).getValue()).isEqualTo(1.);
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), BEVERAGES_RULE_1_ID).get().getValue())
      .isEqualTo(2.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), BEVERAGES_RULE_2_ID).get().getValue())
      .isEqualTo(1.);
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByMatchedRule(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1).extracting(MapResultEntryDto::getValue).containsExactly(2.);
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
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
      .build();
    reportData.getView().setProperties((ViewProperty) null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private DecisionDefinitionEngineDto deployDishDecisionDefinition() {
    return engineIntegrationExtension.deployDecisionDefinition(createDecideDishDecisionDefinition());
  }

  private HashMap<String, InputVariableEntry> createDishInputs(final String season,
                                                               final Integer guestCount,
                                                               final boolean withChildren) {
    return new HashMap<>() {{
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

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByMatchedRule(
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
