/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import static io.camunda.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.property.ImportProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.DraftVariableStore;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.dto.VariableDTO;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.tasklist.webapp.es.TaskValidator;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.tasklist.DraftTaskVariableEntity;
import io.camunda.webapps.schema.entities.tasklist.SnapshotTaskVariableEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskState;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
  @Mock private TaskStore taskStore;
  @Mock private VariableStore variableStore;
  @Mock private DraftVariableStore draftVariableStore;
  @Mock private TasklistProperties tasklistProperties;
  @Mock private TaskValidator taskValidator;
  @Captor private ArgumentCaptor<Collection<DraftTaskVariableEntity>> draftTaskVariableCaptor;
  @Captor private ArgumentCaptor<Collection<SnapshotTaskVariableEntity>> taskVariableCaptor;
  @Spy private ObjectMapper objectMapper = CommonUtils.getObjectMapper();

  @InjectMocks private VariableService instance;

  @Test
  void persistDraftTaskVariablesWhenValidInputShouldPersistVariables() {
    // given
    final var taskId = 123L;
    final var taskIdAsString = String.valueOf(taskId);
    final var flowNodeInstanceId = 456L;
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
        new TaskEntity()
            .setId(taskIdAsString)
            .setKey(taskId)
            .setFlowNodeInstanceId(String.valueOf(flowNodeInstanceId))
            .setProcessInstanceId("123")
            .setTenantId("tenant_a");
    when(taskStore.getTask(taskIdAsString)).thenReturn(task);
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
    instance.persistDraftTaskVariables(taskIdAsString, draftTaskVariables);

    // then
    verify(taskValidator).validateCanPersistDraftTaskVariables(task);
    verify(draftVariableStore).deleteAllByTaskId(taskIdAsString);
    verify(draftVariableStore).createOrUpdate(draftTaskVariableCaptor.capture());

    final Collection<DraftTaskVariableEntity> variablesToPersist =
        draftTaskVariableCaptor.getValue();
    assertThat(variablesToPersist)
        .extracting("id", "name", "value", "isPreview", "fullValue", "tenantId")
        .containsExactlyInAnyOrder(
            tuple("123-varB", "varB", "\"valueB\"", false, "\"valueB\"", "tenant_a"),
            tuple("456-varC", "varC", "\"updateValueC\"", false, "\"updateValueC\"", "tenant_a"),
            tuple(
                "123-varE",
                "varE",
                "\"valueE_duplicate2\"",
                false,
                "\"valueE_duplicate2\"",
                "tenant_a"),
            tuple(
                "123-varF",
                "varF",
                "\"valueF_value_that_exceeds_var",
                true,
                "\"valueF_value_that_exceeds_variableSizeThreshold_limit\"",
                "tenant_a"));
  }

  private void mockReturnOriginalVariables(
      final List<VariableEntity> originalVariables, final String... varNames) {
    final FlowNodeInstanceEntity flowNodeInstance = mock(FlowNodeInstanceEntity.class);
    when(variableStore.getFlowNodeInstances(any())).thenReturn(List.of(flowNodeInstance));
    when(variableStore.getVariablesByFlowNodeInstanceIds(
            any(), varNames.length != 0 ? eq(List.of(varNames)) : any(), eq(emptySet())))
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
      final VariableInputDTO variableInput, final String errorMessage) {
    final String taskId = "taskID_123";
    final List<VariableInputDTO> draftTaskVariables = List.of(variableInput);
    final TaskEntity task = mock(TaskEntity.class);
    when(taskStore.getTask(taskId)).thenReturn(task);

    assertThatThrownBy(() -> instance.persistDraftTaskVariables(taskId, draftTaskVariables))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageStartingWith(errorMessage);

    verifyNoInteractions(variableStore, draftVariableStore);
  }

  @Test
  void getVariableSearchResponsesForCreatedTask() {
    // given
    final String taskId = "taskId_557";
    final var flowNodeInstanceId = 557L;
    final TaskEntity task =
        new TaskEntity()
            .setId(taskId)
            .setFlowNodeInstanceId(String.valueOf(flowNodeInstanceId))
            .setProcessInstanceId("123")
            .setState(TaskState.CREATED);
    when(taskStore.getTask(taskId)).thenReturn(task);
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
            .setId(VariableService.getDraftVariableId(taskId, "A_numVar"))
            .setName("A_numVar")
            .setValue("456")
            .setFullValue("456");
    final var strDraftVariable =
        new DraftTaskVariableEntity()
            .setId(VariableService.getDraftVariableId(taskId, "B_strVar"))
            .setName("B_strVar")
            .setValue("\"strVarValue\"")
            .setFullValue("\"strVarValue\"");
    when(draftVariableStore.getVariablesByTaskIdAndVariableNames(taskId, emptyList()))
        .thenReturn(List.of(numDraftVariable, strDraftVariable));

    // when
    final var result = instance.getVariableSearchResponses(taskId, emptySet());

    // then
    assertThat(result)
        .containsExactly(
            new VariableSearchResponse()
                .setId(String.format("%s-%s", flowNodeInstanceId, "A_numVar"))
                .setName("A_numVar")
                .setValue("123")
                .setPreviewValue("123")
                .setDraft(
                    new VariableSearchResponse.DraftSearchVariableValue()
                        .setPreviewValue("456")
                        .setValue("456")),
            new VariableSearchResponse()
                .setId(VariableService.getDraftVariableId(taskId, "B_strVar"))
                .setName("B_strVar")
                .setDraft(
                    new VariableSearchResponse.DraftSearchVariableValue()
                        .setPreviewValue("\"strVarValue\"")
                        .setValue("\"strVarValue\"")),
            new VariableSearchResponse()
                .setId(String.format("%s-%s", flowNodeInstanceId, "C_objVar"))
                .setName("C_objVar")
                .setValue("{\"propA\":1,\"propB\":\"strVal\"}")
                .setPreviewValue("{\"propA\":1,\"propB\":\"strVal\"}"));
  }

  @Test
  void getVariablesPerTaskIdForCreatedTask() {
    final String taskId = "taskId_557";
    final var flowNodeInstanceId = 557L;
    final int variableSizeThreshold = 100;
    mockReturnOriginalVariables(
        List.of(
            createVariableEntity(flowNodeInstanceId, "A_numVar", "123", variableSizeThreshold),
            createVariableEntity(
                flowNodeInstanceId,
                "C_objVar",
                "{\"propA\":1,\"propB\":\"strVal\"}",
                variableSizeThreshold)),
        "A_numVar",
        "C_objVar");

    // when
    final var result =
        instance.getVariablesPerTaskId(
            List.of(
                new VariableStore.GetVariablesRequest()
                    .setTaskId(taskId)
                    .setFlowNodeInstanceId(String.valueOf(flowNodeInstanceId))
                    .setState(TaskState.CREATED)
                    .setProcessInstanceId("123")
                    .setVarNames(List.of("A_numVar", "C_objVar"))));

    // then
    assertThat(result).size().isEqualTo(1);
    assertThat(result.get(taskId))
        .containsExactly(
            new VariableDTO()
                .setId(String.format("%s-%s", flowNodeInstanceId, "A_numVar"))
                .setName("A_numVar")
                .setValue("123")
                .setPreviewValue("123"),
            new VariableDTO()
                .setId(String.format("%s-%s", flowNodeInstanceId, "C_objVar"))
                .setName("C_objVar")
                .setValue("{\"propA\":1,\"propB\":\"strVal\"}")
                .setIsValueTruncated(false)
                .setPreviewValue("{\"propA\":1,\"propB\":\"strVal\"}"));
  }

  @Test
  void getVariablesPerTaskIdFoCompletedTask() {
    final String taskId = "taskId_557";
    final String flowNodeInstanceId = "flowNodeInstanceId_557";
    final int variableSizeThreshold = 100;
    when(variableStore.getTaskVariablesPerTaskId(
            List.of(
                new VariableStore.GetVariablesRequest()
                    .setTaskId(taskId)
                    .setFlowNodeInstanceId(flowNodeInstanceId)
                    .setState(TaskState.COMPLETED)
                    .setVarNames(List.of("A_numVar", "C_objVar")))))
        .thenReturn(
            Map.of(
                taskId,
                List.of(
                    new SnapshotTaskVariableEntity()
                        .setId("variableId")
                        .setTaskId("id789")
                        .setName("A_numVar")
                        .setIsPreview(true)
                        .setValue("[\"val1\", \"val2\",")
                        .setFullValue("[\"val1\", \"val2\", \"val3\"]"))));

    // when
    final var result =
        instance.getVariablesPerTaskId(
            List.of(
                new VariableStore.GetVariablesRequest()
                    .setTaskId(taskId)
                    .setFlowNodeInstanceId(flowNodeInstanceId)
                    .setState(TaskState.COMPLETED)
                    .setVarNames(List.of("A_numVar", "C_objVar"))));

    // then
    assertThat(result).size().isEqualTo(1);
    assertThat(result.get(taskId))
        .containsExactly(
            new VariableDTO()
                .setId("variableId")
                .setName("A_numVar")
                .setValue("[\"val1\", \"val2\", \"val3\"]")
                .setIsValueTruncated(true)
                .setPreviewValue("[\"val1\", \"val2\","));
  }

  @Test
  void getVariableResponseWhenOnlyOriginalVariableExists() {
    // given
    final String variableId = "123-varA";
    when(variableStore.getRuntimeVariable(variableId, emptySet()))
        .thenReturn(createVariableEntity(123L, "varA", "123", 100));
    when(draftVariableStore.getById(variableId)).thenReturn(Optional.empty());

    // when
    final var result = instance.getVariableResponse(variableId);

    // then
    assertThat(result)
        .isEqualTo(
            new VariableResponse()
                .setId(variableId)
                .setName("varA")
                .setValue("123")
                .setTenantId(DEFAULT_TENANT_IDENTIFIER));
    verify(draftVariableStore).getById(variableId);
    verify(variableStore, never()).getTaskVariable(any(), any());
  }

  @Test
  void getVariableResponseWhenOriginalVariableExistsWithDraftValue() {
    // given
    final String variableId = "123-varB";
    when(variableStore.getRuntimeVariable(variableId, emptySet()))
        .thenReturn(createVariableEntity(123L, "varB", "123", 100));
    when(draftVariableStore.getById(variableId))
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
                .setDraft(new VariableResponse.DraftVariableValue().setValue("557"))
                .setTenantId(DEFAULT_TENANT_IDENTIFIER));
    verify(draftVariableStore).getById(variableId);
    verify(variableStore, never()).getTaskVariable(any(), any());
  }

  @Test
  void getVariableResponseWhenOnlyDraftValueExists() {
    // given
    final String variableId = "id456-strVal";
    when(variableStore.getRuntimeVariable(variableId, emptySet()))
        .thenThrow(NotFoundException.class);
    when(draftVariableStore.getById(variableId))
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
                        .setValue("\"previewValue+fullValue\""))
                .setTenantId(DEFAULT_TENANT_IDENTIFIER));
    verify(draftVariableStore).getById(variableId);
    verify(variableStore, never()).getTaskVariable(any(), any());
  }

  @Test
  void getVariableResponseWhenOnlyTaskVariableExists() {
    // given
    final String variableId = "id789-arrayVar";
    when(variableStore.getRuntimeVariable(variableId, emptySet()))
        .thenThrow(NotFoundException.class);
    when(draftVariableStore.getById(variableId)).thenReturn(Optional.empty());
    when(variableStore.getTaskVariable(variableId, emptySet()))
        .thenReturn(
            new SnapshotTaskVariableEntity()
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
                .setValue("[\"val1\", \"val2\", \"val3\"]")
                .setTenantId(DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  void getVariableResponseWhenNoOriginalDraftAndTaskVariableExistThenNotFoundExceptionExpected() {
    // given
    final String variableId = "idUnknown-var";
    when(variableStore.getRuntimeVariable(variableId, emptySet()))
        .thenThrow(NotFoundException.class);
    when(draftVariableStore.getById(variableId)).thenReturn(Optional.empty());
    when(variableStore.getTaskVariable(variableId, emptySet())).thenThrow(NotFoundException.class);

    // when - then
    assertThatThrownBy(() -> instance.getVariableResponse(variableId))
        .isInstanceOf(NotFoundApiException.class)
        .hasMessage("Variable with id %s not found.", variableId);
  }

  @Test
  void getVariableResponseWhenTaskNotFoundOrTenantWithoutAccess() {
    when(taskStore.getTask("123")).thenThrow(NotFoundException.class);
    assertThatThrownBy(() -> instance.getVariableSearchResponses("123", null))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void persistTaskVariablesWithoutDraftVariables() {
    // given
    final String taskId = "taskId_557";
    final var flowNodeInstanceId = 456L;
    final TaskEntity task =
        new TaskEntity()
            .setId(taskId)
            .setFlowNodeInstanceId(String.valueOf(flowNodeInstanceId))
            .setProcessInstanceId("123")
            .setTenantId("tenant_b");
    when(taskStore.getTask(taskId)).thenReturn(task);
    final ImportProperties importProperties = mock(ImportProperties.class);
    when(tasklistProperties.getImporter()).thenReturn(importProperties);
    final int variableSizeThreshold = 30;
    when(importProperties.getVariableSizeThreshold()).thenReturn(variableSizeThreshold);

    mockReturnOriginalVariables(
        List.of(
            createVariableEntity(
                flowNodeInstanceId, "varA", "\"originalA\"", variableSizeThreshold, "tenant_b"),
            createVariableEntity(
                flowNodeInstanceId, "varB", "\"originalB\"", variableSizeThreshold, "tenant_b")));

    // when
    instance.persistTaskVariables(
        taskId,
        List.of(
            new VariableInputDTO().setName("varB").setValue("\"changedB\""),
            new VariableInputDTO().setName("varC").setValue("\"changedC\"")),
        false);

    // then
    verify(draftVariableStore, never()).getVariablesByTaskIdAndVariableNames(any(), any());
    verify(variableStore).persistTaskVariables(taskVariableCaptor.capture());
    final var variablesToPersist = taskVariableCaptor.getValue();
    assertThat(variablesToPersist)
        .extracting("name", "value", "fullValue", "tenantId")
        .containsExactlyInAnyOrder(
            tuple("varA", "\"originalA\"", "\"originalA\"", "tenant_b"),
            tuple("varB", "\"changedB\"", "\"changedB\"", "tenant_b"),
            tuple("varC", "\"changedC\"", "\"changedC\"", "tenant_b"));
  }

  @Test
  void persistTaskVariablesWithDraftVariables() {
    // given
    final String taskId = "taskId_557";
    final var flowNodeInstanceId = 456L;
    final TaskEntity task =
        new TaskEntity()
            .setId(taskId)
            .setFlowNodeInstanceId(String.valueOf(flowNodeInstanceId))
            .setProcessInstanceId("123")
            .setTenantId("tenant_c");
    when(taskStore.getTask(taskId)).thenReturn(task);
    final ImportProperties importProperties = mock(ImportProperties.class);
    when(tasklistProperties.getImporter()).thenReturn(importProperties);
    final int variableSizeThreshold = 30;
    when(importProperties.getVariableSizeThreshold()).thenReturn(variableSizeThreshold);

    mockReturnOriginalVariables(
        List.of(
            createVariableEntity(
                flowNodeInstanceId, "varA", "\"originalA\"", variableSizeThreshold, "tenant_c"),
            createVariableEntity(
                flowNodeInstanceId, "varB", "\"originalB\"", variableSizeThreshold, "tenant_c")));

    when(draftVariableStore.getVariablesByTaskIdAndVariableNames(taskId, emptyList()))
        .thenReturn(
            List.of(
                new DraftTaskVariableEntity()
                    .setName("varB")
                    .setValue("\"draftB\"")
                    .setFullValue("\"draftB\"")
                    .setTenantId("tenant_c"),
                new DraftTaskVariableEntity()
                    .setName("varC")
                    .setValue("\"draftC\"")
                    .setFullValue("\"draftC\"")
                    .setTenantId("tenant_c"),
                new DraftTaskVariableEntity()
                    .setName("varD")
                    .setValue("\"draftD\"")
                    .setFullValue("\"draftD\"")
                    .setTenantId("tenant_c")));

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
    verify(variableStore).persistTaskVariables(taskVariableCaptor.capture());
    final var variablesToPersist = taskVariableCaptor.getValue();
    assertThat(variablesToPersist)
        .extracting("name", "value", "fullValue", "tenantId")
        .containsExactlyInAnyOrder(
            tuple("varA", "\"originalA\"", "\"originalA\"", "tenant_c"),
            tuple("varB", "\"draftB\"", "\"draftB\"", "tenant_c"),
            tuple("varC", "\"draftC\"", "\"draftC\"", "tenant_c"),
            tuple(
                "varD",
                "\"changedD_longValueThatExceedL",
                "\"changedD_longValueThatExceedLimit\"",
                "tenant_c"),
            tuple("varE", "\"changedE\"", "\"changedE\"", "tenant_c"));
  }

  @Test
  void persistDraftVariablesWhenTaskDoesnExistOrWithoutTenantAccess() {
    when(taskStore.getTask("123")).thenThrow(NotFoundException.class);
    assertThatThrownBy(() -> instance.persistDraftTaskVariables("123", null))
        .isInstanceOf(NotFoundApiException.class);
  }

  private static VariableEntity createVariableEntity(
      final Long flowNodeInstanceId,
      final String name,
      final String value,
      final int variableSizeThreshold,
      final String tenantId) {
    final VariableEntity entity =
        new VariableEntity()
            .setId(String.format("%s-%s", flowNodeInstanceId, name))
            .setName(name)
            .setScopeKey(flowNodeInstanceId);

    if (value.length() > variableSizeThreshold) {
      // store preview
      entity.setValue(value.substring(0, variableSizeThreshold));
      entity.setFullValue(value);
      entity.setIsPreview(true);
    } else {
      entity.setValue(value);
      entity.setFullValue(null);
      entity.setIsPreview(false);
    }
    entity.setTenantId(tenantId);
    return entity;
  }

  private static VariableEntity createVariableEntity(
      final Long flowNodeInstanceId,
      final String name,
      final String value,
      final int variableSizeThreshold) {
    return createVariableEntity(
        flowNodeInstanceId, name, value, variableSizeThreshold, DEFAULT_TENANT_IDENTIFIER);
  }
}
