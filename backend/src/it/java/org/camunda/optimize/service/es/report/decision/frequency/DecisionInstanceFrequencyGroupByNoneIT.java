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
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createNumericInputVariableFilter;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;

public class DecisionInstanceFrequencyGroupByNoneIT extends AbstractDecisionDefinitionIT {

  @Test
  public void reportEvaluationMultiInstancesSpecificVersion() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployAndStartSimpleDecisionDefinition("key");
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).isNotNull().isEqualTo(3.);
  }

  @Test
  public void reportEvaluationMultiInstancesAllVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployAndStartSimpleDecisionDefinition("key");
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getFirstMeasureData()).isNotNull().isEqualTo(5.);
  }

  @Test
  public void reportEvaluationMultiInstancesAllVersionsOtherDefinitionsHaveNoSideEffect() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployAndStartSimpleDecisionDefinition("key");
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    // other decision definition
    deployAndStartSimpleDecisionDefinition("key2");

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getFirstMeasureData()).isNotNull().isEqualTo(5.);
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
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void reportEvaluationMultiInstancesFilter() {
    // given
    final double inputVariableValueToFilterFor = 200.0;
    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
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

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(String.valueOf(decisionDefinitionDto.getVersion()))
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .setFilter(createNumericInputVariableFilter(
        INPUT_AMOUNT_ID, FilterOperator.GREATER_THAN_EQUALS, String.valueOf(inputVariableValueToFilterFor)
      ))
      .build();
    final ReportResultResponseDto<Double> result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isNotNull().isEqualTo(2.);
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
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
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    reportData.getGroupBy().setType(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

}
