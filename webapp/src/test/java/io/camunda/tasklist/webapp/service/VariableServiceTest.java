/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.DraftTaskVariableEntity;
import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.property.ImportProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.es.DraftVariablesReaderWriter;
import io.camunda.tasklist.webapp.es.TaskReaderWriter;
import io.camunda.tasklist.webapp.es.TaskValidator;
import io.camunda.tasklist.webapp.es.VariableReaderWriter;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VariableServiceTest {

  @Mock private TaskReaderWriter taskReaderWriter;
  @Mock private VariableReaderWriter variableReaderWriter;
  @Mock private DraftVariablesReaderWriter draftVariablesReaderWriter;
  @Mock private TasklistProperties tasklistProperties;
  @Mock private TaskValidator taskValidator;
  @Captor private ArgumentCaptor<Collection<DraftTaskVariableEntity>> draftTaskVariableCaptor;
  @Captor private ArgumentCaptor<Collection<TaskVariableEntity>> taskVariableCaptor;
  @Spy private ObjectMapper objectMapper = CommonUtils.getObjectMapper();

  @InjectMocks private VariableService instance;

  @Test
  void persistDraftTaskVariablesWhenValidInputShouldPersistVariables() {
    // given
    final String taskId = "taskId_123";
    final String flowNodeInstanceId = "flowNodeInstanceId_456";
    final List<VariableInputDTO> draftTaskVariables =
        List.of(
            new VariableInputDTO().setName("varA").setValue("\"valueA\""),
            new VariableInputDTO().setName("varB").setValue("\"valueB\""),
            new VariableInputDTO().setName("varC").setValue("\"updateValueC\""),
            new VariableInputDTO().setName("varE").setValue("\"valueE_duplicate1\""),
            new VariableInputDTO().setName("varE").setValue("\"valueE_duplicate2\""),
            new VariableInputDTO()
                .setName("varF")
                .setValue("\"valueF_value_that_exceeds_variableSizeThreshold_limit\""));
    final TaskEntity task =
        new TaskEntity().setId(taskId).setFlowNodeInstanceId(flowNodeInstanceId);
    when(taskReaderWriter.getTask(taskId)).thenReturn(task);
    final ImportProperties importProperties = mock(ImportProperties.class);
    when(tasklistProperties.getImporter()).thenReturn(importProperties);
    final int variableSizeThreshold = 30;
    when(importProperties.getVariableSizeThreshold()).thenReturn(variableSizeThreshold);
    mockReturnOriginalVariables(
        List.of(
            createVariableEntity(flowNodeInstanceId, "varA", "\"valueA\"", variableSizeThreshold),
            createVariableEntity(flowNodeInstanceId, "varC", "\"valueC\"", variableSizeThreshold),
            createVariableEntity(flowNodeInstanceId, "varD", "\"valueD\"", variableSizeThreshold)));

    // when
    instance.persistDraftTaskVariables(taskId, draftTaskVariables);

    // then
    verify(taskValidator).validateCanPersistDraftTaskVariables(task);
    verify(draftVariablesReaderWriter).deleteAllByTaskId(taskId);
    verify(draftVariablesReaderWriter).createOrUpdate(draftTaskVariableCaptor.capture());

    final Collection<DraftTaskVariableEntity> variablesToPersist =
        draftTaskVariableCaptor.getValue();
    assertThat(variablesToPersist)
        .extracting("id", "name", "value", "isPreview", "fullValue")
        .containsExactlyInAnyOrder(
            tuple("taskId_123-varB", "varB", "\"valueB\"", false, "\"valueB\""),
            tuple(
                "flowNodeInstanceId_456-varC",
                "varC",
                "\"updateValueC\"",
                false,
                "\"updateValueC\""),
            tuple(
                "taskId_123-varE", "varE", "\"valueE_duplicate2\"", false, "\"valueE_duplicate2\""),
            tuple(
                "taskId_123-varF",
                "varF",
                "\"valueF_value_that_exceeds_var",
                true,
                "\"valueF_value_that_exceeds_variableSizeThreshold_limit\""));
  }

  private void mockReturnOriginalVariables(List<VariableEntity> originalVariables) {
    final FlowNodeInstanceEntity flowNodeInstance = mock(FlowNodeInstanceEntity.class);
    when(variableReaderWriter.getFlowNodeInstances(any())).thenReturn(List.of(flowNodeInstance));
    when(variableReaderWriter.getVariablesByFlowNodeInstanceIds(any(), any(), any()))
        .thenReturn(originalVariables);
  }

  private static Stream<Arguments> persistDraftTaskVariablesInvalidInputTestData() {
    return Stream.of(
        Arguments.of(
            new VariableInputDTO().setName("wrongStringValue").setValue("wrongStr"),
            "Unrecognized token 'wrongStr': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false'"),
        Arguments.of(
            new VariableInputDTO().setName("wrongArrayValue").setValue("[\"group1\",\"group2\""),
            "Unexpected end-of-input: expected close marker for Array (start marker at [Source"),
        Arguments.of(
            new VariableInputDTO()
                .setName("wrongObjectArrayValue")
                .setValue("[{\"code\":\"123\",\"name\":\"ABC-001\"},{\"code\":\"111.653.365]"),
            "Unexpected end-of-input: was expecting closing quote for a string value"));
  }

  @ParameterizedTest
  @MethodSource("persistDraftTaskVariablesInvalidInputTestData")
  void persistDraftTaskVariablesWhenInvalidInputShouldThrowException(
      VariableInputDTO variableInput, String errorMessage) {
    final String taskId = "taskID_123";
    final List<VariableInputDTO> draftTaskVariables = List.of(variableInput);
    final TaskEntity task = mock(TaskEntity.class);
    when(taskReaderWriter.getTask(taskId)).thenReturn(task);

    assertThatThrownBy(() -> instance.persistDraftTaskVariables(taskId, draftTaskVariables))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageStartingWith(errorMessage);

    verifyNoInteractions(variableReaderWriter, draftVariablesReaderWriter);
  }

  @Test
  void getVariableSearchResponsesForCreatedTask() {
    // given
    final String taskId = "taskId_557";
    final String flowNodeInstanceId = "flowNodeInstanceId_557";
    final TaskEntity task =
        new TaskEntity()
            .setId(taskId)
            .setFlowNodeInstanceId(flowNodeInstanceId)
            .setState(TaskState.CREATED);
    when(taskReaderWriter.getTask(taskId)).thenReturn(task);
    final int variableSizeThreshold = 100;
    mockReturnOriginalVariables(
        List.of(
            createVariableEntity(flowNodeInstanceId, "A_numVar", "123", variableSizeThreshold),
            createVariableEntity(
                flowNodeInstanceId,
                "C_objVar",
                "{\"propA\":1,\"propB\":\"strVal\"}",
                variableSizeThreshold)));
    final var numDraftVariable =
        new DraftTaskVariableEntity()
            .setId(DraftTaskVariableEntity.getIdBy(taskId, "A_numVar"))
            .setName("A_numVar")
            .setValue("456")
            .setFullValue("456");
    final var strDraftVariable =
        new DraftTaskVariableEntity()
            .setId(DraftTaskVariableEntity.getIdBy(taskId, "B_strVar"))
            .setName("B_strVar")
            .setValue("\"strVarValue\"")
            .setFullValue("\"strVarValue\"");
    when(draftVariablesReaderWriter.getVariablesByTaskIdAndVariableNames(
            taskId, Collections.emptyList()))
        .thenReturn(List.of(numDraftVariable, strDraftVariable));

    // when
    final var result = instance.getVariableSearchResponses(taskId, Collections.emptyList());

    // then
    assertThat(result)
        .containsExactly(
            new VariableSearchResponse()
                .setId(VariableEntity.getIdBy(flowNodeInstanceId, "A_numVar"))
                .setName("A_numVar")
                .setValue("123")
                .setPreviewValue("123")
                .setDraft(
                    new VariableSearchResponse.DraftSearchVariableValue()
                        .setPreviewValue("456")
                        .setValue("456")),
            new VariableSearchResponse()
                .setId(DraftTaskVariableEntity.getIdBy(taskId, "B_strVar"))
                .setName("B_strVar")
                .setDraft(
                    new VariableSearchResponse.DraftSearchVariableValue()
                        .setPreviewValue("\"strVarValue\"")
                        .setValue("\"strVarValue\"")),
            new VariableSearchResponse()
                .setId(VariableEntity.getIdBy(flowNodeInstanceId, "C_objVar"))
                .setName("C_objVar")
                .setValue("{\"propA\":1,\"propB\":\"strVal\"}")
                .setPreviewValue("{\"propA\":1,\"propB\":\"strVal\"}"));
  }

  @Test
  void getVariableResponseWhenOnlyOriginalVariableExists() {
    // given
    final String variableId = "id123-varA";
    when(variableReaderWriter.getRuntimeVariable(variableId, Collections.emptySet()))
        .thenReturn(createVariableEntity("id123", "varA", "123", 100));
    when(draftVariablesReaderWriter.getById(variableId)).thenReturn(Optional.empty());

    // when
    final var result = instance.getVariableResponse(variableId);

    // then
    assertThat(result)
        .isEqualTo(new VariableResponse().setId(variableId).setName("varA").setValue("123"));
    verify(draftVariablesReaderWriter).getById(variableId);
    verify(variableReaderWriter, never()).getTaskVariable(any(), any());
  }

  @Test
  void getVariableResponseWhenOriginalVariableExistsWithDraftValue() {
    // given
    final String variableId = "id123-varB";
    when(variableReaderWriter.getRuntimeVariable(variableId, Collections.emptySet()))
        .thenReturn(createVariableEntity("id123", "varB", "123", 100));
    when(draftVariablesReaderWriter.getById(variableId))
        .thenReturn(
            Optional.of(
                new DraftTaskVariableEntity()
                    .setId(variableId)
                    .setName("varB")
                    .setValue("557")
                    .setFullValue("557")));

    // when
    final var result = instance.getVariableResponse(variableId);

    // then
    assertThat(result)
        .isEqualTo(
            new VariableResponse()
                .setId(variableId)
                .setName("varB")
                .setValue("123")
                .setDraft(new VariableResponse.DraftVariableValue().setValue("557")));
    verify(draftVariablesReaderWriter).getById(variableId);
    verify(variableReaderWriter, never()).getTaskVariable(any(), any());
  }

  @Test
  void getVariableResponseWhenOnlyDraftValueExists() {
    // given
    final String variableId = "id456-strVal";
    when(variableReaderWriter.getRuntimeVariable(variableId, Collections.emptySet()))
        .thenThrow(NotFoundException.class);
    when(draftVariablesReaderWriter.getById(variableId))
        .thenReturn(
            Optional.of(
                new DraftTaskVariableEntity()
                    .setId(variableId)
                    .setName("strVal")
                    .setIsPreview(true)
                    .setValue("\"previewValue")
                    .setFullValue("\"previewValue+fullValue\"")));

    // when
    final var result = instance.getVariableResponse(variableId);

    // then
    assertThat(result)
        .isEqualTo(
            new VariableResponse()
                .setId(variableId)
                .setName("strVal")
                .setValue(null)
                .setDraft(
                    new VariableResponse.DraftVariableValue()
                        .setValue("\"previewValue+fullValue\"")));
    verify(draftVariablesReaderWriter).getById(variableId);
    verify(variableReaderWriter, never()).getTaskVariable(any(), any());
  }

  @Test
  void getVariableResponseWhenOnlyTaskVariableExists() {
    // given
    final String variableId = "id789-arrayVar";
    when(variableReaderWriter.getRuntimeVariable(variableId, Collections.emptySet()))
        .thenThrow(NotFoundException.class);
    when(draftVariablesReaderWriter.getById(variableId)).thenReturn(Optional.empty());
    when(variableReaderWriter.getTaskVariable(variableId, Collections.emptySet()))
        .thenReturn(
            new TaskVariableEntity()
                .setId(variableId)
                .setTaskId("id789")
                .setName("arrayVar")
                .setIsPreview(true)
                .setValue("[\"val1\", \"val2\",")
                .setFullValue("[\"val1\", \"val2\", \"val3\"]"));

    // when
    final var result = instance.getVariableResponse(variableId);

    // then
    assertThat(result)
        .isEqualTo(
            new VariableResponse()
                .setId(variableId)
                .setName("arrayVar")
                .setValue("[\"val1\", \"val2\", \"val3\"]"));
  }

  @Test
  void getVariableResponseWhenNoOriginalDraftAndTaskVariableExistThenNotFoundExceptionExpected() {
    // given
    final String variableId = "idUnknown-var";
    when(variableReaderWriter.getRuntimeVariable(variableId, Collections.emptySet()))
        .thenThrow(NotFoundException.class);
    when(draftVariablesReaderWriter.getById(variableId)).thenReturn(Optional.empty());
    when(variableReaderWriter.getTaskVariable(variableId, Collections.emptySet()))
        .thenThrow(NotFoundException.class);

    // when - then
    assertThatThrownBy(() -> instance.getVariableResponse(variableId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Variable with id %s not found.", variableId);
  }

  @Test
  void persistTaskVariablesWithoutDraftVariables() {
    // given
    final String taskId = "taskId_557";
    final String flowNodeInstanceId = "flowNodeInstanceId_456";
    final TaskEntity task =
        new TaskEntity().setId(taskId).setFlowNodeInstanceId(flowNodeInstanceId);
    when(taskReaderWriter.getTask(taskId)).thenReturn(task);
    final ImportProperties importProperties = mock(ImportProperties.class);
    when(tasklistProperties.getImporter()).thenReturn(importProperties);
    final int variableSizeThreshold = 30;
    when(importProperties.getVariableSizeThreshold()).thenReturn(variableSizeThreshold);

    mockReturnOriginalVariables(
        List.of(
            createVariableEntity(
                flowNodeInstanceId, "varA", "\"originalA\"", variableSizeThreshold),
            createVariableEntity(
                flowNodeInstanceId, "varB", "\"originalB\"", variableSizeThreshold)));

    // when
    instance.persistTaskVariables(
        taskId,
        List.of(
            new VariableInputDTO().setName("varB").setValue("\"changedB\""),
            new VariableInputDTO().setName("varC").setValue("\"changedC\"")),
        false);

    // then
    verify(draftVariablesReaderWriter, never()).getVariablesByTaskIdAndVariableNames(any(), any());
    verify(variableReaderWriter).persistTaskVariables(taskVariableCaptor.capture());
    final var variablesToPersist = taskVariableCaptor.getValue();
    assertThat(variablesToPersist)
        .extracting("name", "value", "fullValue")
        .containsExactlyInAnyOrder(
            tuple("varA", "\"originalA\"", "\"originalA\""),
            tuple("varB", "\"changedB\"", "\"changedB\""),
            tuple("varC", "\"changedC\"", "\"changedC\""));
  }

  @Test
  void persistTaskVariablesWithDraftVariables() {
    // given
    final String taskId = "taskId_557";
    final String flowNodeInstanceId = "flowNodeInstanceId_456";
    final TaskEntity task =
        new TaskEntity().setId(taskId).setFlowNodeInstanceId(flowNodeInstanceId);
    when(taskReaderWriter.getTask(taskId)).thenReturn(task);
    final ImportProperties importProperties = mock(ImportProperties.class);
    when(tasklistProperties.getImporter()).thenReturn(importProperties);
    final int variableSizeThreshold = 30;
    when(importProperties.getVariableSizeThreshold()).thenReturn(variableSizeThreshold);

    mockReturnOriginalVariables(
        List.of(
            createVariableEntity(
                flowNodeInstanceId, "varA", "\"originalA\"", variableSizeThreshold),
            createVariableEntity(
                flowNodeInstanceId, "varB", "\"originalB\"", variableSizeThreshold)));

    when(draftVariablesReaderWriter.getVariablesByTaskIdAndVariableNames(
            taskId, Collections.emptyList()))
        .thenReturn(
            List.of(
                new DraftTaskVariableEntity()
                    .setName("varB")
                    .setValue("\"draftB\"")
                    .setFullValue("\"draftB\""),
                new DraftTaskVariableEntity()
                    .setName("varC")
                    .setValue("\"draftC\"")
                    .setFullValue("\"draftC\""),
                new DraftTaskVariableEntity()
                    .setName("varD")
                    .setValue("\"draftD\"")
                    .setFullValue("\"draftD\"")));

    // when
    instance.persistTaskVariables(
        taskId,
        List.of(
            new VariableInputDTO()
                .setName("varD")
                .setValue("\"changedD_longValueThatExceedLimit\""),
            new VariableInputDTO().setName("varE").setValue("\"changedE\"")),
        true);

    // then
    verify(variableReaderWriter).persistTaskVariables(taskVariableCaptor.capture());
    final var variablesToPersist = taskVariableCaptor.getValue();
    assertThat(variablesToPersist)
        .extracting("name", "value", "fullValue")
        .containsExactlyInAnyOrder(
            tuple("varA", "\"originalA\"", "\"originalA\""),
            tuple("varB", "\"draftB\"", "\"draftB\""),
            tuple("varC", "\"draftC\"", "\"draftC\""),
            tuple(
                "varD", "\"changedD_longValueThatExceedL", "\"changedD_longValueThatExceedLimit\""),
            tuple("varE", "\"changedE\"", "\"changedE\""));
  }

  private static VariableEntity createVariableEntity(
      String flowNodeInstanceId, String name, String value, int variableSizeThreshold) {
    final VariableEntity entity =
        new VariableEntity()
            .setId(VariableEntity.getIdBy(flowNodeInstanceId, name))
            .setName(name)
            .setScopeFlowNodeId(flowNodeInstanceId);

    if (value.length() > variableSizeThreshold) {
      // store preview
      entity.setValue(value.substring(0, variableSizeThreshold));
      entity.setIsPreview(true);
    } else {
      entity.setValue(value);
    }
    entity.setFullValue(value);
    return entity;
  }
}
