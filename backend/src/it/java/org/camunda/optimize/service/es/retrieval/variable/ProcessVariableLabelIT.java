/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import com.google.common.collect.ImmutableList;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.LabelDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, "a label 1"),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.STRING, "a label 2")
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
    DefinitionVariableLabelsDto definitionVariableLabelsDto = new DefinitionVariableLabelsDto(
      processEngineDtos.get(0).getKey(),
      List.of(FIRST_LABEL, SECOND_LABEL)
    );
    executeUpdateProcessVariablesLabelRequest(definitionVariableLabelsDto, ACCESS_TOKEN);

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
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, "a label 1"),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.STRING, "a label 2"),
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, null),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.STRING, null)
      );
  }

  @Test
  public void getVariableNamesAndLabelsForMultipleDefinitionsWithIdenticalVariablesAndLabels() {
    // given
    List<ProcessDefinitionEngineDto> processEngineDtos =
      deployProcessDefinitionsAndStartProcessInstancesWithVariables();
    DefinitionVariableLabelsDto definitionVariableLabelsDto = new DefinitionVariableLabelsDto(
      processEngineDtos.get(0).getKey(),
      List.of(FIRST_LABEL, SECOND_LABEL)
    );
    executeUpdateProcessVariablesLabelRequest(definitionVariableLabelsDto, ACCESS_TOKEN);

    DefinitionVariableLabelsDto definitionVariableLabelsDto2 = new DefinitionVariableLabelsDto(
      processEngineDtos.get(1).getKey(),
      List.of(FIRST_LABEL, SECOND_LABEL)
    );
    executeUpdateProcessVariablesLabelRequest(definitionVariableLabelsDto2, ACCESS_TOKEN);

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
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, "a label 1"),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.STRING, "a label 2")
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

    DefinitionVariableLabelsDto definitionVariableLabelsDto = new DefinitionVariableLabelsDto(
      processDefinition.getKey(),
      List.of(FIRST_LABEL)
    );
    executeUpdateProcessVariablesLabelRequest(definitionVariableLabelsDto, ACCESS_TOKEN);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(
      List.of(
        createProcessVariableRequestDto(processDefinition.getKey())
      ));

    // then the duplicate labelled variables are deduplicated from the result set
    assertThat(variableResponse)
      .hasSize(2)
      .containsExactlyInAnyOrder(
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, "a label 1"),
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

    DefinitionVariableLabelsDto definitionVariableLabelsDto1 = new DefinitionVariableLabelsDto(
      processDefinition1.getKey(),
      List.of(firstLabel, secondLabel, thirdLabel)
    );
    executeUpdateProcessVariablesLabelRequest(definitionVariableLabelsDto1, ACCESS_TOKEN);

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
        new ProcessVariableNameResponseDto(FIRST_VARIABLE_NAME, VariableType.STRING, "a label 1"),
        new ProcessVariableNameResponseDto(SECOND_VARIABLE_NAME, VariableType.STRING, "a label 2")
      );

    // when
    final LabelDto deletedFirstLabel = new LabelDto("", FIRST_VARIABLE_NAME, VariableType.STRING);
    DefinitionVariableLabelsDto labelOptimizeDtoDeletedLabel = new DefinitionVariableLabelsDto(
      PROCESS_DEFINITION_KEY,
      List.of(deletedFirstLabel)
    );
    executeUpdateProcessVariablesLabelRequest(labelOptimizeDtoDeletedLabel, ACCESS_TOKEN);
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
        Tuple.tuple(SECOND_VARIABLE_NAME, VariableType.STRING, "a label 2")
      );
  }

  public ProcessDefinitionEngineDto deployDefinitionWithLabels(final LabelDto firstLabelDto,
                                                               final LabelDto secondLabelDto) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put(FIRST_VARIABLE_NAME, "value1");
    variables.put(SECOND_VARIABLE_NAME, "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();
    DefinitionVariableLabelsDto definitionVariableLabelsDto = new DefinitionVariableLabelsDto(
      processDefinition.getKey(),
      List.of(firstLabelDto, secondLabelDto)
    );
    executeUpdateProcessVariablesLabelRequest(definitionVariableLabelsDto, ACCESS_TOKEN);
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

  private void executeUpdateProcessVariablesLabelRequest(DefinitionVariableLabelsDto labelOptimizeDto,
                                                         String accessToken) {
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableLabelRequest(labelOptimizeDto, accessToken)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  private ProcessVariableNameRequestDto createProcessVariableRequestDto(String processDefinitionKey) {
    return new ProcessVariableNameRequestDto(
      processDefinitionKey,
      Collections.singletonList(ALL_VERSIONS),
      DEFAULT_TENANT_IDS
    );
  }

}
