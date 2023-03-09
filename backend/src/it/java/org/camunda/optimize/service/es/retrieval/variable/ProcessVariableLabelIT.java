/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.LabelDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;

public class ProcessVariableLabelIT extends AbstractVariableIT {

  final String FIRST_VARIABLE_NAME = "first variable";
  final String SECOND_VARIABLE_NAME = "second variable";
  final LabelDto FIRST_LABEL = new LabelDto("a label 1", FIRST_VARIABLE_NAME, VariableType.STRING);
  final LabelDto SECOND_LABEL = new LabelDto("a label 2", SECOND_VARIABLE_NAME, VariableType.STRING);
  final String ACCESS_TOKEN = "aToken";

  @BeforeEach
  public void setup() {
    embeddedOptimizeExtension.getConfigurationService()
      .getOptimizeApiConfiguration()
      .setAccessToken(ACCESS_TOKEN);
  }

  @Test
  public void getVariableNamesAndLabelsForSingleDefinition() {
    // given
    ProcessDefinitionEngineDto processDefinitionEngineDto = deployDefinitionWithLabels(FIRST_LABEL, SECOND_LABEL);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(
      processDefinitionEngineDto);

    // then
    assertThat(variableResponse)
      .hasSize(2)
      .containsExactlyInAnyOrder(
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, FIRST_LABEL.getVariableLabel()),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.STRING, SECOND_LABEL.getVariableLabel())
      );
  }

  @Test
  public void getVariableNamesAndLabelsWhenNoLabelExists() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(FIRST_VARIABLE_NAME, "value1");
    variables.put(SECOND_VARIABLE_NAME, "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(processDefinition);

    // then
    assertThat(variableResponse)
      .hasSize(2)
      .containsExactlyInAnyOrder(
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, null),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.STRING, null)
      );
  }

  @Test
  public void getVariableNamesAndLabelsForMultipleDefinitions() {
    // given
    List<ProcessDefinitionEngineDto> processEngineDtos =
      deployProcessDefinitionsAndStartProcessInstancesWithVariables();
    addVariableLabelsToDefinition(processEngineDtos.get(0).getKey(), List.of(FIRST_LABEL, SECOND_LABEL));

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(
      Arrays.asList(
        createProcessVariableRequestDto(processEngineDtos.get(0).getKey()),
        createProcessVariableRequestDto(processEngineDtos.get(1).getKey())
      ));

    // then
    assertThat(variableResponse)
      .hasSize(4)
      .containsExactlyInAnyOrder(
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, FIRST_LABEL.getVariableLabel()),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.STRING, SECOND_LABEL.getVariableLabel()),
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, null),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.STRING, null)
      );
  }

  @Test
  public void getVariableNamesAndLabelsForMultipleDefinitionsWithIdenticalVariablesAndLabels() {
    // given
    List<ProcessDefinitionEngineDto> processEngineDtos =
      deployProcessDefinitionsAndStartProcessInstancesWithVariables();
    addVariableLabelsToDefinition(processEngineDtos.get(0).getKey(), List.of(FIRST_LABEL, SECOND_LABEL));
    addVariableLabelsToDefinition(processEngineDtos.get(1).getKey(), List.of(FIRST_LABEL, SECOND_LABEL));

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(
      Arrays.asList(
        createProcessVariableRequestDto(processEngineDtos.get(0).getKey()),
        createProcessVariableRequestDto(processEngineDtos.get(1).getKey())
      ));

    // then
    assertThat(variableResponse)
      .hasSize(2)
      .containsExactlyInAnyOrder(
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, FIRST_LABEL.getVariableLabel()),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.STRING, SECOND_LABEL.getVariableLabel())
      );
  }

  @Test
  public void getVariableNamesAndLabelsForVariablesWithAndWithoutLabel() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(FIRST_VARIABLE_NAME, "value1");
    variables.put(SECOND_VARIABLE_NAME, "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    addVariableLabelsToDefinition(processDefinition.getKey(), List.of(FIRST_LABEL));

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(
      List.of(
        createProcessVariableRequestDto(processDefinition.getKey())
      ));

    // then the duplicate labelled variables are deduplicated from the result set
    assertThat(variableResponse)
      .hasSize(2)
      .containsExactlyInAnyOrder(
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, FIRST_LABEL.getVariableLabel()),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.STRING, null)
      );
  }

  @Test
  public void getVariableNamesAndLabelsForVariablesOfDifferentTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(FIRST_VARIABLE_NAME, "value1");
    variables.put(SECOND_VARIABLE_NAME, 42);
    variables.put("third variable", true);
    engineIntegrationExtension.startProcessInstance(processDefinition1.getId(), variables);
    importAllEngineEntitiesFromScratch();

    final LabelDto firstLabel = new LabelDto("string variable", FIRST_VARIABLE_NAME, VariableType.STRING);
    final LabelDto secondLabel = new LabelDto("integer variable", SECOND_VARIABLE_NAME, VariableType.INTEGER);
    final LabelDto thirdLabel = new LabelDto("boolean variable", "third variable", VariableType.BOOLEAN);
    addVariableLabelsToDefinition(processDefinition1.getKey(), List.of(firstLabel, secondLabel, thirdLabel));

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(
      List.of(
        createProcessVariableRequestDto(processDefinition1.getKey())
      ));

    // then
    assertThat(variableResponse)
      .hasSize(3)
      .containsExactlyInAnyOrder(
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, "string variable"),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.INTEGER, "integer variable"),
        new ProcessVariableNameResponseDto("third variable", VariableType.BOOLEAN, "boolean variable")
      );
  }

  @Test
  public void variableLabelNotBeingFetchedAfterLabelHasBeenDeleted() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployDefinitionWithLabels(FIRST_LABEL, SECOND_LABEL);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(processDefinition);

    // then
    assertThat(variableResponse)
      .hasSize(2)
      .containsExactlyInAnyOrder(
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, FIRST_LABEL.getVariableLabel()),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.STRING, SECOND_LABEL.getVariableLabel())
      );

    // when
    final LabelDto deletedFirstLabel = new LabelDto("", FIRST_VARIABLE_NAME, VariableType.STRING);
    addVariableLabelsToDefinition(processDefinition.getKey(), List.of(deletedFirstLabel));
    variableResponse = variablesClient.getProcessVariableNames(processDefinition);

    // then
    assertThat(variableResponse)
      .hasSize(2)
      .extracting(
        ProcessVariableNameResponseDto::getName,
        ProcessVariableNameResponseDto::getType,
        ProcessVariableNameResponseDto::getLabel
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple(FIRST_VARIABLE_NAME, VariableType.STRING, null),
        Tuple.tuple(SECOND_VARIABLE_NAME, VariableType.STRING, SECOND_LABEL.getVariableLabel())
      );
  }

  @Test
  public void variableLabelBeingFetchedCorrectlyForReportsInDashboards() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    ProcessInstanceEngineDto startedInstance = startProcessInstanceWithVariables(processDefinition.getId());
    startedInstance.setProcessDefinitionKey(processDefinition.getKey());
    importAllEngineEntitiesFromScratch();
    addVariableLabelsToDefinition(processDefinition.getKey(), List.of(FIRST_LABEL, SECOND_LABEL));
    final String firstReportId = createAndSaveReportForDeployedInstance(startedInstance).getId();
    final String secondReportId = createAndSaveReportForDeployedInstance(startedInstance).getId();
    createEmptyDashboardAndAddReports(List.of(firstReportId, secondReportId), null);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNamesForReportIds(List.of(
      firstReportId,
      secondReportId
    ));

    // then
    assertThat(variableResponse)
      .hasSize(2)
      .extracting(
        ProcessVariableNameResponseDto::getName,
        ProcessVariableNameResponseDto::getType,
        ProcessVariableNameResponseDto::getLabel
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple(FIRST_VARIABLE_NAME, VariableType.STRING, FIRST_LABEL.getVariableLabel()),
        Tuple.tuple(SECOND_VARIABLE_NAME, VariableType.STRING, SECOND_LABEL.getVariableLabel())
      );
  }

  @Test
  public void variableLabelBeingFetchedCorrectlyForReportCopiesInDashboards() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    ProcessInstanceEngineDto startedInstance = startProcessInstanceWithVariables(processDefinition.getId());
    startedInstance.setProcessDefinitionKey(processDefinition.getKey());
    importAllEngineEntitiesFromScratch();
    addVariableLabelsToDefinition(processDefinition.getKey(), List.of(FIRST_LABEL, SECOND_LABEL));
    final String firstReportId = createAndSaveReportForDeployedInstance(startedInstance).getId();
    final String copiedReport = copyReport(firstReportId);
    createEmptyDashboardAndAddReports(List.of(firstReportId, copiedReport), null);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNamesForReportIds(List.of(
      firstReportId,
      copiedReport
    ));

    // then
    assertThat(variableResponse)
      .hasSize(2)
      .extracting(
        ProcessVariableNameResponseDto::getName,
        ProcessVariableNameResponseDto::getType,
        ProcessVariableNameResponseDto::getLabel
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple(FIRST_VARIABLE_NAME, VariableType.STRING, FIRST_LABEL.getVariableLabel()),
        Tuple.tuple(SECOND_VARIABLE_NAME, VariableType.STRING, SECOND_LABEL.getVariableLabel())
      );
  }

  private void createEmptyDashboardAndAddReports(final List<String> reportIds, final String collectionId) {
    final DashboardDefinitionRestDto dashboardDefinitionDto = new DashboardDefinitionRestDto();
    dashboardDefinitionDto.setTiles(reportIds.stream()
                                        .map(id -> DashboardReportTileDto.builder()
                                          .id(id)
                                          .type(DashboardTileType.OPTIMIZE_REPORT)
                                          .build())
                                        .collect(Collectors.toList()));
    Optional.ofNullable(collectionId).ifPresent(dashboardDefinitionDto::setCollectionId);
    dashboardClient.createDashboard(dashboardDefinitionDto);
  }

  private SingleProcessReportDefinitionRequestDto createAndSaveReportForDeployedInstance
    (final ProcessInstanceEngineDto deployedInstanceWithAllVariables) {
    final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      reportClient.createSingleProcessReportDefinitionDto(
        null,
        deployedInstanceWithAllVariables.getProcessDefinitionKey(),
        Collections.singletonList(null)
      );
    singleProcessReportDefinitionDto.getData().setProcessDefinitionVersion(ALL_VERSIONS);
    final String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
    return reportClient.getSingleProcessReportDefinitionDto(reportId);
  }

  public ProcessDefinitionEngineDto deployDefinitionWithLabels(final LabelDto firstLabelDto,
                                                               final LabelDto secondLabelDto) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startProcessInstanceWithVariables(processDefinition.getId());
    importAllEngineEntitiesFromScratch();
    DefinitionVariableLabelsDto definitionVariableLabelsDto = new DefinitionVariableLabelsDto(
      processDefinition.getKey(),
      List.of(firstLabelDto, secondLabelDto)
    );
    executeUpdateProcessVariablesLabelRequest(definitionVariableLabelsDto);
    return processDefinition;
  }

  private List<ProcessDefinitionEngineDto> deployProcessDefinitionsAndStartProcessInstancesWithVariables() {
    final ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition();
    final ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition("someKey", null);
    Map<String, Object> variables = new HashMap<>();
    variables.put(FIRST_VARIABLE_NAME, "value1");
    variables.put(SECOND_VARIABLE_NAME, "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition1.getId(), variables);
    engineIntegrationExtension.startProcessInstance(processDefinition2.getId(), variables);
    importAllEngineEntitiesFromScratch();
    return List.of(processDefinition1, processDefinition2);
  }

  private void executeUpdateProcessVariablesLabelRequest(final DefinitionVariableLabelsDto labelOptimizeDto) {
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableLabelRequest(labelOptimizeDto)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  private ProcessInstanceEngineDto startProcessInstanceWithVariables(final String processDefinitionId) {
    Map<String, Object> variables = new HashMap<>();
    variables.put(FIRST_VARIABLE_NAME, "value1");
    variables.put(SECOND_VARIABLE_NAME, "value2");
    return engineIntegrationExtension.startProcessInstance(processDefinitionId, variables);
  }

  private ProcessVariableNameRequestDto createProcessVariableRequestDto(final String processDefinitionKey) {
    return new ProcessVariableNameRequestDto(
      processDefinitionKey,
      Collections.singletonList(ALL_VERSIONS),
      DEFAULT_TENANT_IDS
    );
  }

  private void addVariableLabelsToDefinition(final String processDefinitionKey, final List<LabelDto> labelsToAdd) {
    DefinitionVariableLabelsDto definitionVariableLabelsDto = new DefinitionVariableLabelsDto(
      processDefinitionKey,
      labelsToAdd
    );
    executeUpdateProcessVariablesLabelRequest(definitionVariableLabelsDto);
  }

  private String copyReport(final String reportId) {
    return reportClient.copyReportToCollection(reportId, null).readEntity(IdResponseDto.class).getId();
  }

}
