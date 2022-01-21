/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.LabelDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_LABEL_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessVariableLabelRestServiceIT extends AbstractIT {

  final LabelDto FIRST_LABEL = new LabelDto("a label 1", "a name", VariableType.STRING);
  final LabelDto SECOND_LABEL = new LabelDto("a label 2", "an other name", VariableType.STRING);
  final String PROCESS_DEFINITION_KEY = "someProcessDefinition";
  final String ACCESS_TOKEN = "aToken";

  @BeforeEach
  public void setup() {
    embeddedOptimizeExtension.getConfigurationService()
      .getOptimizeApiConfiguration()
      .setAccessToken(ACCESS_TOKEN);
  }

  @Test
  public void updateVariableLabelsWithoutAccessToken() {
    // given
    DefinitionVariableLabelsDto definitionVariableLabelsDto = new DefinitionVariableLabelsDto(PROCESS_DEFINITION_KEY, Collections.emptyList());

    // when
    Response response = executeUpdateProcessVariableLabelRequest(definitionVariableLabelsDto, null);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void updateVariableLabelsEmptyUpdateList() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    DefinitionVariableLabelsDto definitionVariableLabelsDto = new DefinitionVariableLabelsDto(PROCESS_DEFINITION_KEY, Collections.emptyList());

    // when
    Response response = executeUpdateProcessVariableLabelRequest(definitionVariableLabelsDto, ACCESS_TOKEN);

    // then
    assertThat(getAllDocumentsOfVariableLabelIndex())
      .singleElement().satisfies(labelsDtos -> assertThat(labelsDtos.getLabels()).isEmpty());
  }

  @Test
  public void updateVariableLabelWithEmptyLabelForVariableWhichDoesNotExist() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    final LabelDto emptyLabelName = new LabelDto("", "a variable name", VariableType.STRING);
    final LabelDto whiteSpaceLabel = new LabelDto(" ", "a second name", VariableType.STRING);
    DefinitionVariableLabelsDto labelOptimizeDto = new DefinitionVariableLabelsDto(
      PROCESS_DEFINITION_KEY,
      List.of(emptyLabelName, whiteSpaceLabel)
    );

    // when
    Response response = executeUpdateProcessVariableLabelRequest(labelOptimizeDto, ACCESS_TOKEN);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getAllDocumentsOfVariableLabelIndex()).singleElement()
      .satisfies(labelDtos -> assertThat(labelDtos.getLabels()).isEmpty());
  }

  @Test
  public void storeVariableLabels() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    DefinitionVariableLabelsDto labelOptimizeDto = new DefinitionVariableLabelsDto(
      PROCESS_DEFINITION_KEY,
      List.of(FIRST_LABEL, SECOND_LABEL)
    );

    // when
    Response response = executeUpdateProcessVariableLabelRequest(labelOptimizeDto, ACCESS_TOKEN);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getAllDocumentsOfVariableLabelIndex()).singleElement().satisfies(labelsDtos -> {
      assertThat(labelsDtos.getLabels()).containsExactlyInAnyOrder(FIRST_LABEL, SECOND_LABEL);
    });
  }

  @Test
  public void storeVariableLabelsWithSameLabelValues() {
    // given
    final LabelDto otherLabelSameValue = new LabelDto("a label 1", "a different name", VariableType.STRING);
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    DefinitionVariableLabelsDto labelOptimizeDto = new DefinitionVariableLabelsDto(
      PROCESS_DEFINITION_KEY,
      List.of(FIRST_LABEL, otherLabelSameValue)
    );

    // when
    Response response = executeUpdateProcessVariableLabelRequest(labelOptimizeDto, ACCESS_TOKEN);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getAllDocumentsOfVariableLabelIndex()).singleElement().satisfies(labelsDtos -> {
      assertThat(labelsDtos.getLabels()).containsExactlyInAnyOrder(FIRST_LABEL, otherLabelSameValue);
    });
  }

  @Test
  public void deleteVariableLabels() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    LabelDto deletedFirstLabel = new LabelDto("", "a name", VariableType.STRING);
    DefinitionVariableLabelsDto labelOptimizeDto1 = new DefinitionVariableLabelsDto(
      PROCESS_DEFINITION_KEY,
      List.of(FIRST_LABEL, SECOND_LABEL)
    );
    DefinitionVariableLabelsDto labelOptimizeDto2 = new DefinitionVariableLabelsDto(PROCESS_DEFINITION_KEY, List.of(deletedFirstLabel));
    Response response = executeUpdateProcessVariableLabelRequest(labelOptimizeDto1, ACCESS_TOKEN);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // when
    response = executeUpdateProcessVariableLabelRequest(labelOptimizeDto2, ACCESS_TOKEN);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getAllDocumentsOfVariableLabelIndex()).singleElement().satisfies(labelsDtos -> {
      assertThat(labelsDtos.getLabels()).containsExactlyInAnyOrder(SECOND_LABEL);
    });
  }

  @Test
  public void updateVariableLabel() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    LabelDto updatedFirstLabel = new LabelDto(
      "a label 3",
      FIRST_LABEL.getVariableName(),
      FIRST_LABEL.getVariableType()
    );
    DefinitionVariableLabelsDto labelOptimizeDto1 = new DefinitionVariableLabelsDto(
      PROCESS_DEFINITION_KEY,
      List.of(FIRST_LABEL, SECOND_LABEL)
    );
    DefinitionVariableLabelsDto labelOptimizeDto2 = new DefinitionVariableLabelsDto(PROCESS_DEFINITION_KEY, List.of(updatedFirstLabel));
    Response response = executeUpdateProcessVariableLabelRequest(labelOptimizeDto1, ACCESS_TOKEN);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // when
    response = executeUpdateProcessVariableLabelRequest(labelOptimizeDto2, ACCESS_TOKEN);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getAllDocumentsOfVariableLabelIndex()).singleElement().satisfies(labelsDtos -> {
      assertThat(labelsDtos.getLabels()).containsExactlyInAnyOrder(SECOND_LABEL, updatedFirstLabel);
    });
  }

  @Test
  public void storeVariableLabelsForSameVariable() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    LabelDto updatedFirstLabel = new LabelDto(
      "a second label",
      FIRST_LABEL.getVariableName(),
      FIRST_LABEL.getVariableType()
    );
    DefinitionVariableLabelsDto labelOptimizeDto1 = new DefinitionVariableLabelsDto(
      PROCESS_DEFINITION_KEY,
      List.of(FIRST_LABEL, updatedFirstLabel)
    );

    // when
    Response response = executeUpdateProcessVariableLabelRequest(labelOptimizeDto1, ACCESS_TOKEN);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("blankStrings")
  public void storeVariableLabelWithEmptyAndNullDefinitionKey(final String processDefinitionKey) {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    LabelDto updatedFirstLabel = new LabelDto(
      "doesntMatter",
      FIRST_LABEL.getVariableName(),
      FIRST_LABEL.getVariableType()
    );
    DefinitionVariableLabelsDto labelOptimizeDto1 = new DefinitionVariableLabelsDto(
      processDefinitionKey,
      List.of(FIRST_LABEL, updatedFirstLabel)
    );

    // when
    Response response = executeUpdateProcessVariableLabelRequest(labelOptimizeDto1, ACCESS_TOKEN);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void storeVariableLabelWithNullVariableType() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    LabelDto updatedFirstLabel = new LabelDto("doesntMatter", "doesntMatter", null);
    DefinitionVariableLabelsDto labelOptimizeDto1 = new DefinitionVariableLabelsDto(
      PROCESS_DEFINITION_KEY,
      List.of(FIRST_LABEL, updatedFirstLabel)
    );

    // when
    Response response = executeUpdateProcessVariableLabelRequest(labelOptimizeDto1, ACCESS_TOKEN);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("blankStrings")
  public void storeVariableLabelWithNullAndEmptyName(final String variableName) {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    LabelDto updatedFirstLabel = new LabelDto("doesntMatter", variableName, VariableType.DATE);
    DefinitionVariableLabelsDto labelOptimizeDto1 = new DefinitionVariableLabelsDto(
      PROCESS_DEFINITION_KEY,
      List.of(FIRST_LABEL, updatedFirstLabel)
    );

    // when
    Response response = executeUpdateProcessVariableLabelRequest(labelOptimizeDto1, ACCESS_TOKEN);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void storeVariableLabelForNonExistentDefinition() {
    // given
    DefinitionVariableLabelsDto labelOptimizeDto = new DefinitionVariableLabelsDto(PROCESS_DEFINITION_KEY, List.of(FIRST_LABEL));

    // when
    Response response = executeUpdateProcessVariableLabelRequest(labelOptimizeDto, ACCESS_TOKEN);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  public List<DefinitionVariableLabelsDto> getAllDocumentsOfVariableLabelIndex() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      VARIABLE_LABEL_INDEX_NAME,
      DefinitionVariableLabelsDto.class
    );
  }

  public Response executeUpdateProcessVariableLabelRequest(DefinitionVariableLabelsDto labelOptimizeDto, String accessToken) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableLabelRequest(labelOptimizeDto, accessToken)
      .execute();
  }

  private static Stream<String> blankStrings() {
    return Stream.of("", null);
  }

}
