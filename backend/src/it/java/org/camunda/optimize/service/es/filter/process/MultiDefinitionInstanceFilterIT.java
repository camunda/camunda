/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;

public class MultiDefinitionInstanceFilterIT extends AbstractFilterIT {

  private static final String DEFINITION_KEY_1 = "key1";
  private static final String DEFINITION_IDENTIFIER_1 = "id1";
  private static final String DEFINITION_KEY_2 = "key2";
  private static final String DEFINITION_IDENTIFIER_2 = "id2";
  private static final String VAR_NAME = "var1";
  private static final String VAR_VALUE = "val1";
  private static final Map<String, Object> VARIABLE_MAP = Map.of(VAR_NAME, VAR_VALUE);

  @Test
  public void multipleDefinitionSpecificFiltersOnlyApplyToInstanceDataOfDefinitionSetInAppliedTo() {
    // given
    final ProcessInstanceEngineDto completedProcessInstanceDefinition1 = engineIntegrationExtension
      .deployAndStartProcessWithVariables(BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY_1), VARIABLE_MAP);
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition1.getId());
    final ProcessInstanceEngineDto runningProcessInstanceDefinition1 = engineIntegrationExtension
      .startProcessInstance(completedProcessInstanceDefinition1.getDefinitionId());

    final ProcessInstanceEngineDto completedProcessInstanceDefinition2 = engineIntegrationExtension
      .deployAndStartProcessWithVariables(BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY_2), VARIABLE_MAP);
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition2.getId());
    engineIntegrationExtension.startProcessInstance(completedProcessInstanceDefinition2.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createRawDataReportWithTwoDefinitions();
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .completedInstancesOnly()
        .appliedTo(DEFINITION_IDENTIFIER_2)
        .add()
        // completedProcessInstanceDefinition1 has the variable with excluded value, thus should not be returned
        .variable().name(VAR_NAME).type(STRING).operator(NOT_IN).value(VAR_VALUE)
        .appliedTo(DEFINITION_IDENTIFIER_1)
        .add()
        .buildList()

    );
    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> rawDataReportResultDto = reportClient
      .evaluateRawReport(reportData)
      .getResult();

    // then
    assertThat(rawDataReportResultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(rawDataReportResultDto.getInstanceCountWithoutFilters()).isEqualTo(4L);
    assertThat(rawDataReportResultDto.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        runningProcessInstanceDefinition1.getId(),
        completedProcessInstanceDefinition2.getId()
      );
  }

  @Test
  public void definitionSpecificFilterOnlyAppliesToInstanceDataOfSpecificDefinitionVersion() {
    // given
    final ProcessInstanceEngineDto completedProcessInstanceDefinition1 = engineIntegrationExtension
      .deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY_1));
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition1.getId());
    final ProcessInstanceEngineDto runningProcessInstanceDefinition1 = engineIntegrationExtension
      .startProcessInstance(completedProcessInstanceDefinition1.getDefinitionId());

    // same definition key but new version
    final ProcessInstanceEngineDto completedProcessInstanceDefinition2 = engineIntegrationExtension
      .deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY_1));
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition2.getId());
    engineIntegrationExtension.startProcessInstance(completedProcessInstanceDefinition2.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createRawDataReportWithTwoDefinitions();
    reportData.setDefinitions(List.of(
      new ReportDataDefinitionDto(DEFINITION_IDENTIFIER_1, DEFINITION_KEY_1, List.of("1")),
      new ReportDataDefinitionDto(DEFINITION_IDENTIFIER_2, DEFINITION_KEY_1, List.of("2"))
    ));
    reportData.setFilter(
      ProcessFilterBuilder.filter().completedInstancesOnly().appliedTo(DEFINITION_IDENTIFIER_2).add().buildList()
    );
    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> rawDataReportResultDto = reportClient
      .evaluateRawReport(reportData)
      .getResult();

    // then
    assertThat(rawDataReportResultDto.getInstanceCount()).isEqualTo(3L);
    assertThat(rawDataReportResultDto.getInstanceCountWithoutFilters()).isEqualTo(4L);
    assertThat(rawDataReportResultDto.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        completedProcessInstanceDefinition1.getId(),
        runningProcessInstanceDefinition1.getId(),
        completedProcessInstanceDefinition2.getId()
      );
  }

  @ParameterizedTest
  @MethodSource("allDefinitionsAppliedToValues")
  public void definitionFilterIsAppliedToAllDefinitionsPresent(final List<String> appliedTo) {
    // given
    final ProcessInstanceEngineDto completedProcessInstanceDefinition1 = engineIntegrationExtension
      .deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY_1));
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition1.getId());
    engineIntegrationExtension.startProcessInstance(completedProcessInstanceDefinition1.getDefinitionId());

    final ProcessInstanceEngineDto completedProcessInstanceDefinition2 = engineIntegrationExtension
      .deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY_2));
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstanceDefinition2.getId());
    engineIntegrationExtension.startProcessInstance(completedProcessInstanceDefinition2.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createRawDataReportWithTwoDefinitions();
    reportData.setFilter(
      ProcessFilterBuilder.filter().completedInstancesOnly().appliedTo(appliedTo).add().buildList()
    );
    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> rawDataReportResultDto = reportClient
      .evaluateRawReport(reportData).getResult();

    // then
    assertThat(rawDataReportResultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(rawDataReportResultDto.getInstanceCountWithoutFilters()).isEqualTo(4L);
    assertThat(rawDataReportResultDto.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        completedProcessInstanceDefinition1.getId(),
        completedProcessInstanceDefinition2.getId()
      );
  }

  private ProcessReportDataDto createRawDataReportWithTwoDefinitions() {
    return TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .definitions(List.of(
        new ReportDataDefinitionDto(DEFINITION_IDENTIFIER_1, DEFINITION_KEY_1),
        new ReportDataDefinitionDto(DEFINITION_IDENTIFIER_2, DEFINITION_KEY_2)
      ))
      .build();
  }

  private static Stream<Arguments> allDefinitionsAppliedToValues() {
    return Stream.<Arguments>builder()
      .add(Arguments.of((List<String>) null))
      .add(Arguments.of(List.of(APPLIED_TO_ALL_DEFINITIONS)))
      .add(Arguments.of(List.of(DEFINITION_IDENTIFIER_1, DEFINITION_IDENTIFIER_2)))
      .build();
  }
}
